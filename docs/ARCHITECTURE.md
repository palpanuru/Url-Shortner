# Architecture overview

## 1. What the system does

A URL shortener with three capability areas:

| Area | Capabilities |
|---|---|
| Core | Create short links (random code or custom alias), redirect, inspect, delete |
| Analytics | Per-click capture (async), total counter, daily series, top referrers, browser families |
| Reliability & safety | Expiry and purge, API-key auth with per-client ownership, rate limits, URL safety policy, redirect cache, bounded async processing, health probes, metrics |

## 2. Components

```mermaid
flowchart LR
    subgraph Clients
        B[Browser / end user]
        C[API client<br/>X-API-Key]
    end

    subgraph App["Spring Boot service (stateless, horizontally scalable)"]
        direction TB
        F1[ApiKeyAuthFilter<br/>/api/** only] --> F2[RateLimitFilter<br/>create per client, redirect per IP]
        F2 --> RC[RedirectController<br/>GET /code]
        F2 --> LC[LinkController<br/>/api/v1/links]
        F2 --> AC[AnalyticsController<br/>/api/v1/links/code/stats]

        RC --> S[ShortLinkService]
        LC --> S
        S --> UP[UrlPolicy / AliasPolicy]
        S --> CG[CodeGenerator<br/>SecureRandom Base62]
        S --> LCa[(LinkCache<br/>Caffeine, 60s)]
        S -- LinkClickedEvent --> EB{{Spring events}}
        EB -- "@Async (bounded pool)" --> CR[ClickRecorder]
        AC --> AS[AnalyticsService]
        P[ExpiredLinkPurger<br/>@Scheduled] --> S
    end

    DB[(PostgreSQL / H2<br/>Flyway-managed schema)]
    B --> F1
    C --> F1
    S --> DB
    CR --> DB
    AS --> DB
```

Package layout: one package per feature, so a change to analytics doesn't touch link code:

```
com.urlshortener
├── link/        core domain: entity, repository, service, policies, controllers, cache, purger
├── analytics/   click events, recorder (async consumer), stats service + API
├── security/    API-key auth, token-bucket rate limiting, path normalisation
├── config/      typed, validated properties; async executor; clock; OpenAPI
└── web/         RFC 9457 problem-detail mapping (MVC advice + filter writer)
```

## 3. Request flows

**Create** `POST /api/v1/links`
1. `ApiKeyAuthFilter` hashes the presented key (SHA-256), compares it in constant time with the configured hashes, and sets `clientId`.
2. `RateLimitFilter` takes a token from the `create:<clientId>` bucket. If none is left it returns 429 with `Retry-After`.
3. Bean Validation runs, then `UrlPolicy` (scheme, host, credentials, self-reference, private IPs, denylist) and `AliasPolicy` (format, reserved paths).
4. Expiry is resolved: the default TTL, or the caller's value bounded by the max TTL.
5. Insert. Random codes retry on unique-constraint collision (bounded attempts). Aliases are pre-checked, and the constraint still covers the race.

**Redirect** `GET /{code}` is the hot path and is read-only.
1. Rate limit per client IP.
2. `LinkCache` read-through. Only hits are cached, so a 404 can't outlive a new alias.
3. The expiry check runs after the cache read, so a cached link never outlives its expiry. Expired links return 410 Gone.
4. Publish `LinkClickedEvent` and return `302` + `Cache-Control: private, max-age=0`.

**Analytics** run asynchronously.
1. `ClickRecorder` runs on the `analyticsExecutor` pool (2 threads, queue of 10,000).
2. An atomic `UPDATE … SET click_count = click_count + 1` plus an insert into `click_event` (UTC day, referrer host, UA family).
3. When the queue is full the event is dropped and counted (`analytics.events.dropped`). The redirect is never delayed.

## 4. Data model (Flyway V1 → V3)

```
short_link                                   click_event
-----------------------------------------    ------------------------------------------
id            bigint identity PK             id             bigint identity PK
code          varchar(32)  UNIQUE            link_id        bigint FK → short_link ON DELETE CASCADE
target_url    varchar(2048)                  clicked_at     timestamptz
created_at    timestamptz                    clicked_on     date      (UTC; GROUP BY key)
click_count   bigint                         referrer_host  varchar(255) NULL
expires_at    timestamptz  (V3, indexed)     agent_family   varchar(32)
owner_id      varchar(64)  (V3)              INDEX (link_id, clicked_on)
```

Flyway owns the schema and Hibernate only validates it (`ddl-auto: validate`). The migrations are portable across H2 (PostgreSQL mode) and PostgreSQL 16, and both are tested.

## 5. Key decisions

| # | Decision | Alternatives considered | Why |
|---|---|---|---|
| D1 | Random 7-char Base62 codes + retry on unique constraint | Sequential ID → Base62; hash of URL | Sequential codes are enumerable (private links leak). Hashing makes two users share a link and its analytics. 62⁷ ≈ 3.5×10¹² keeps collisions rare |
| D2 | `302`, not `301` | 301 Permanent | Browsers cache 301s forever, which hides repeat clicks and keeps deleted or expired links working client-side |
| D3 | Async analytics on a bounded executor, drop on overflow | Synchronous write; unbounded queue; Kafka | A synchronous write caused the v1 lost-update bug and puts a DB write on every redirect. An unbounded queue risks OOM. Kafka is right for production but too heavy for a prototype. Redirect availability matters more than complete analytics |
| D4 | Atomic SQL increment | Optimistic locking; pessimistic lock | One statement and no retries. Locking would serialise hot links |
| D5 | Store only coarse click attributes | Store IP / full referrer / raw UA | Data minimisation in a regulated (financial) setting. Referrer URLs often carry tokens |
| D6 | API keys stored as SHA-256 hashes, constant-time compare | Plain keys; Spring Security + OAuth2 | Leaked config doesn't yield credentials. Spring Security/OAuth2 is the production path, but the ownership model carries over unchanged |
| D7 | Non-owner gets 404, not 403 | 403 Forbidden | 403 would confirm that the code exists to a client that doesn't own it |
| D8 | In-process token buckets (Caffeine-bounded) | Redis + Bucket4j; gateway limits | No new infrastructure. Limits are per instance (documented). The gateway or Redis is the production answer |
| D9 | Caffeine cache, positive hits only, 60 s TTL | No cache; Redis | Removes the DB read from most redirects. The staleness window after a delete in multi-node setups is bounded and documented |
| D10 | No DNS resolution in `UrlPolicy` | Resolve and check IPs | Adds latency and an external dependency, and DNS rebinding bypasses it anyway. We never fetch targets, so this isn't SSRF |
| D11 | Expired links answer 410 for 30 days, then are purged | Delete at expiry; keep forever | 410 tells clients *why* a link stopped working. Purging bounds storage |
| D12 | Spring Boot 3.5.x (not 4.x) | Boot 4.x | Mature ecosystem (springdoc, SpotBugs), fits typical enterprise baselines. The upgrade is mechanical when needed |

## 6. Execution approach (how the work was done)

Each requirement went through the same engineer-controlled loop. [`AI_WORKFLOW.md`](AI_WORKFLOW.md) records what the AI produced and what was changed or rejected at each step.

```
Requirement → normalise + list ambiguities → decompose into tasks (deps, acceptance criteria)
   → per task: prompt with intent/constraints/criteria → AI draft → engineer review
   → tests (write failing test first for bugs) → quality gates → evidence → commit (tag per scenario)
```

Git history is the audit trail. Each scenario is a tagged commit (`v1-greenfield`, `v2-brownfield`, `v3-ambiguous`) followed by gate and fix commits, and every tag builds green on its own.

## 7. Scaling path (beyond the prototype)

| Concern | Prototype | Production |
|---|---|---|
| Read load | Caffeine per node | + Redis or CDN edge cache for hot codes, read replicas |
| Analytics | In-memory bounded queue | Kafka/SQS → consumer with batched inserts → columnar store (ClickHouse/BigQuery) |
| Rate limiting | Per-node buckets | API gateway / Redis-backed Bucket4j |
| Auth | Hashed API keys in config | OAuth2 client credentials via Spring Security; keys in a vault |
| Purge | Every node runs a bulk DELETE | ShedLock, batched deletes, partitioned `click_event` |
| Codes | Random + retry | Same. If the collision rate rises, raise the length (config only) |

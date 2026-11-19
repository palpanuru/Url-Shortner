# Scenario 3: Ambiguous requirement. "Links should be safe and shouldn't live forever"

**Tag:** `v3-ambiguous` (+ security fix `bbd543f`) · **Result:** 103 tests green on H2 and PostgreSQL

## 1. Requirement as received
> "Links should be safe and shouldn't live forever. We've had complaints about abuse."

No acceptance criteria, no numbers, and three undefined words: *safe*, *forever* and *abuse*.

## 2. Breaking down the ambiguity

| Phrase | Possible meanings | Who'd decide | Chosen interpretation |
|---|---|---|---|
| "safe" (for whom?) | (a) end users clicking: phishing, malware, internal-network links; (b) link owners: others deleting or reading their links; (c) the service: spam generation, scraping | Security + Product | **All three**, each with a minimal control |
| "shouldn't live forever" | Fixed TTL for all? Caller-chosen? Expire on inactivity? Delete or disable? | Product + Legal (retention) | Every link expires; caller may choose, bounded; expired → 410, purged later |
| "abuse" | Spam link creation, code enumeration/scraping, redirect flooding | Security | Rate-limit creation per client and redirects per IP |

### Assumption log
Each assumption is written so a stakeholder can accept or override it. All values are configuration, not code.

| ID | Assumption | Default | Override | Open question for stakeholders |
|---|---|---|---|---|
| A-1 | Every link expires | 365 days | `app.links.default-ttl` | Are permanent links needed for some clients? |
| A-2 | Callers may set expiry up to a maximum | 730 days | `app.links.max-ttl` | Is 2 years acceptable to Legal / records retention? |
| A-3 | Expired links answer **410 Gone** for a grace period, then are purged with their analytics | 30 days | `app.links.purge-after` | Must analytics outlive the link (aggregate before purge)? |
| A-4 | Existing (pre-V3) links: 365 days from creation, **but never less than 30 days from deployment** | — | migration V3 | Should old links be grandfathered as permanent instead? |
| A-5 | "Safe for owners" = authenticated management API; each link owned by the creating client; non-owners see 404 | API key | `app.security.api-keys` | Is OAuth2 / SSO required now or later? |
| A-6 | Pre-V3 links have no known owner → assigned `legacy`, manageable by no client until an operator reassigns them | — | migration V3 | Who owns legacy links? |
| A-7 | Abuse limits | create: 20 burst, 60/min per client; redirect: 200 burst, 1,200/min per IP | `app.rate-limit.*` | Expected peak traffic per client / per NAT'd office IP? |
| A-8 | "Safe for users" = block embedded credentials, self-links, private/loopback/link-local/CGNAT IP literals, configurable domain denylist. **No live reputation lookup** | denylist empty | `app.links.blocked-domains` | Integrate Google Safe Browsing / internal threat intel? |

**Explicitly out of scope**, so nobody assumes it was done: malware scanning of target content, link previews or interstitial warning pages, CAPTCHA, per-user (rather than per-client) identity.

## 3. Decomposition

| # | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| X1 | Typed config: `LinkProperties`, `SecurityProperties`, `RateLimitProperties` (validated, fail fast) | — | Bad config refuses to start |
| X2 | Migration V3: `expires_at`, `owner_id`, backfill per A-4/A-6, then `NOT NULL`, index | — | Runs on H2 and PG; legacy rows get ≥ 30 days |
| X3 | Expiry on create (default/bounded) + 410 on redirect | X1, X2 | Past or over-max expiry → 422 |
| X4 | Scheduled purge (idempotent bulk delete, cascades) | X2 | Nothing purged inside the grace window |
| X5 | API-key filter (SHA-256, constant-time), ownership checks, 404 for non-owners | X1, X2 | 401 without/with bad key; other client gets 404 |
| X6 | Token-bucket limiter (bounded memory) + filter + 429/Retry-After | X5 | Burst then reject; independent per key |
| X7 | URL safety rules (A-8) without DNS lookups | X1 | Table-driven tests for every rule |
| X8 | Redirect cache (positive hits only, expiry-aware) | X3 | Expired link never served from cache |
| X9 | Tests + live verification (smoke, load, rate limit, migration on PG) | all | Evidence captured |

## 4. Execution notes
* **Migration safety (X2).** The backfill runs before `SET NOT NULL`. `GREATEST(created_at + 365d, now + 30d)` stops a two-year-old link from dying on deploy day. This was verified on PostgreSQL with a 800-day-old row ([evidence](../evidence/v3-backfill-postgres.txt)).
* **Private-address check (X7) never touches DNS.** IPv4 literals are parsed octet by octet into `InetAddress.getByAddress`, and bracketed IPv6 literals make `getByName` fail rather than fall back to a lookup. A naive `InetAddress.getByName(host)` on something like `999.1.1.1` *would* trigger a DNS query, which the engineer rejected (#R8).
* **Redirect route pattern.** `{code:[A-Za-z0-9_-]{3,32}}` relies on nested braces inside a path variable regex. That was replaced with `+` and length is enforced by the alias policy, to avoid depending on parser edge cases (#R6).
* **Security review finding, fixed after the scenario commit.** The filters matched on the *raw* request URI, so `/api;x/v1/links` and `/%61pi/v1/links` skipped authentication while Spring MVC still routed them. They failed closed (500, because the controller requires the client ID), but it was still a bypass of the auth layer. The fix matches on Spring's normalised lookup path, and a regression test fails without it ([evidence](../evidence/auth-path-bypass.txt), #R9).

## 5. Validation

| Check | Result |
|---|---|
| Unit: `UrlPolicyTest` (37 cases incl. `169.254.169.254`, `[fd00::1]`, `user@host`, self-link, denylist suffix trick) | ✔ |
| Unit: `TokenBucketTest` (burst, refill, cap, Retry-After, per-key isolation) | ✔ |
| Integration: `ExpiryIntegrationTest` with a controllable clock: 302 → (+2 h) 410 → purge no-op inside grace → (+31 d) purged → 404 | ✔ |
| Integration: `SecurityIntegrationTest`: 401s, owner isolation (404), path-obfuscation regression | ✔ |
| Integration: `RateLimitIntegrationTest`: per-client create limit, per-IP redirect limit, 429 + Retry-After | ✔ |
| Live, default limits: 1,000 rapid redirects from one IP ([evidence](../evidence/ratelimit-default.txt)) | 221 × 302, 779 × 429 |
| Live smoke on PostgreSQL ([evidence](../evidence/smoke-postgres.txt)) | `javascript:` → 422, metadata IP → 422, no key → 401, delete → 404 |

## 6. Residual risks
| Risk | Severity | Mitigation / next step |
|---|---|---|
| Rate limits are per instance | Medium | Gateway or Redis-backed buckets |
| Per-IP redirect limit can hit many users behind one NAT | Medium | Tune A-7 with real traffic; exempt known egress IPs |
| Domain denylist is static; a target can turn malicious after creation | Medium | Safe Browsing check at create + periodic re-scan; ability to disable a link |
| Cache keeps a deleted link alive on other nodes for ≤ 60 s | Low | Pub/sub invalidation or shared cache |
| API keys in config (hashed) | Low–Med | Secrets manager; rotation; OAuth2 |

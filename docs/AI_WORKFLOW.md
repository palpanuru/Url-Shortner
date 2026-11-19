# AI-assisted engineering workflow and traceability log

> **Principle:** AI drafts within a task; the engineer defines the task, reviews every change, decides, and owns the result.

## 1. Setup and roles

| Role | Who / what | Responsibility |
|---|---|---|
| Engineer (owner) | Rajeshwar Palpanuru | Defines tasks and acceptance criteria, reviews all diffs, decides on trade-offs, signs off high-impact changes, owns correctness and production readiness |
| AI assistant | Claude (Anthropic) in an agentic coding session with a shell | Drafts code, tests and docs; runs builds, tests and tools; proposes designs; flags risks |
| Quality gates | Maven `verify` + CI | Objective pass/fail independent of both: tests, coverage, static/security analysis, dependency scan |

## 2. How tasks were given to the AI (prompt contract)

Every task followed the same structure, so the output could be checked against something explicit:

```
INTENT        What outcome, for whom (e.g. "redirect must never wait on analytics")
CONTEXT       Relevant files/modules, current behaviour, schema, constraints of the stack
CONSTRAINTS   Non-negotiables (no DNS lookups in validation; Flyway owns schema; no PII stored;
              portable SQL H2+PG; no new infra)
ACCEPTANCE    Observable checks (tests to add, status codes, metrics, evidence to capture)
OUT OF SCOPE  What not to touch
```

Refinement was iterative: draft → run tests/tools → read failures → narrow prompt → re-run. Real examples are in the log below.

## 3. Secure AI usage rules applied

* **No secrets or proprietary data in prompts.** Only synthetic data (`example.com`, RFC 5737 IPs, test keys).
* **Credentials.** No real keys anywhere. Config holds SHA-256 hashes, and the demo key is labelled local-only.
* **Dependencies.** Every AI-suggested dependency was checked against Maven Central for the current version and pinned. Nothing outside mainstream Spring/Apache/Caffeine was added.
* **Generated code is untrusted until verified.** It passes tests, SpotBugs + FindSecBugs, and review, and security-relevant code also gets an adversarial probe (see R9).
* **Human sign-off is required** for changes marked ⚠ below: schema migrations, auth, data retention, public API contract.

## 4. Traceability log

Legend: **G** = generated and accepted after review · **E** = generated, then edited · **R** = rejected or replaced (with rationale). These are the actual events of this build, in order.

### Generated and accepted (after review and tests)

| # | Artifact | Scenario | Review notes |
|---|---|---|---|
| G1 | Project skeleton, `pom.xml`, `application.yml` | 1 | Versions checked against Maven Central and bumped to latest 3.5.x patch |
| G2 | `CodeGenerator`, `AliasPolicy`, `CodeGeneratorTest`, `AliasPolicyTest` | 1 | — |
| G3 | `ShortLinkService.create` retry loop (non-transactional by design) | 1 | Engineer confirmed the rollback-only reasoning |
| G4 | ⚠ `V1`, `V2` migrations | 1, 2 | Portable SQL confirmed on H2 and PostgreSQL 16 |
| G5 | `ClickAttributes` (referrer → host, UA → family) + 16 table-driven tests | 2 | Ordering (Edge/Opera before Chrome, Chrome before Safari) verified by test |
| G6 | `AnalyticsService` zero-filled daily series | 2 | — |
| G7 | `TokenBucket`, `RateLimiter` with injectable nano-clock | 3 | Deterministic tests, no sleeps |
| G8 | ⚠ `ApiKeyAuthFilter`: SHA-256 + `MessageDigest.isEqual`, loops all clients | 3 | Constant-time behaviour reviewed |
| G9 | ⚠ `V3` migration with grace-period backfill | 3 | Verified on PostgreSQL with legacy rows |
| G10 | Dockerfile, compose, CI workflow, smoke and load scripts | gates | CI not executed in this environment (see limitations) |

### Edited after review

| # | What the AI produced | What changed | Why |
|---|---|---|---|
| E1 | `LinkStats` record holding caller-supplied lists | Added `List.copyOf` in the compact constructor | SpotBugs EI_EXPOSE_REP. A genuine finding, fixed rather than suppressed |
| E2 | `UrlPolicy` constructor that may throw | Class made `final` | SpotBugs CT_CONSTRUCTOR_THROW (finalizer attack needs a subclass) |
| E3 | SpotBugs: 16 findings on first run | 2 fixed (E1, E2). 14 EI_EXPOSE_REP2 on Spring constructor injection suppressed **only** for bean classes, with written rationale in `spotbugs-exclude.xml` | Avoid blanket suppression; DTOs are still checked |
| E4 | Alias create relied only on the DB constraint | Added `existsByCode` pre-check; the constraint still covers the race | Avoids an ERROR-level SQL log on every normal alias conflict |
| E5 | Timestamps returned with nanosecond precision (seen in smoke output) | Truncated to microseconds | PostgreSQL stores μs, so the create response would disagree with later reads of the same link |
| E6 | Integration tests used fixed aliases | Unique per run | The suite failed on a second run against a persistent PostgreSQL DB (2 failures), showing the tests assumed a clean DB |
| E7 | Metrics counters registered on first use | Also registered at startup | Dashboards and alerts need an explicit zero; the load test showed the metric didn't exist before the first drop |

### Rejected or replaced

| # | Rejected | Replaced with | How it was caught | Rationale |
|---|---|---|---|---|
| R1 | Sequential ID → Base62 codes; 301 redirects; hashing URL → code | Random codes; 302; one link per create | Design review | Enumerability, caching, shared analytics (ARCHITECTURE D1, D2) |
| R2 | `spring.mvc.problemdetails.enabled: true` alongside custom advice | Single `GlobalExceptionHandler` | **Failing test** `missingUrlFailsBeanValidation` | Two advices competed, and Boot's generic message won |
| R3 | v1 synchronous load-modify-save counter | Event + atomic `UPDATE` | **Failing concurrency test**: 37/200 counted | Lost updates + write lock on the hot path |
| R4 | `try/catch` around persistence inside `@Transactional @Async` listener | Let it propagate to `AsyncUncaughtExceptionHandler` (log + metric) | Code review | A swallowed exception still leaves the transaction rollback-only; the commit then throws anyway |
| R5 | `MeterRegistry` injected into `AsyncConfigurer` | `ObjectProvider<MeterRegistry>` (lazy) | Code review | AsyncConfigurer is created early, which would initialise the registry before its customisers |
| R6 | `{code:[A-Za-z0-9_-]{3,32}}` route regex | `{code:[A-Za-z0-9_-]+}` | Code review | Avoid relying on nested-brace parsing in path patterns |
| R7 | WARN log per dropped analytics event | Metric only | **Load test** (8,826 log lines in ~7 s under overload) | Logging under overload amplifies the overload |
| R8 | `InetAddress.getByName(host)` for private-IP check | Manual octet parse + bracketed IPv6 | Code review | Invalid literals like `999.1.1.1` fall back to a **DNS lookup** |
| R9 | ⚠ Filters matching on raw `request.getRequestURI()` | Spring's normalised lookup path (`UrlPathHelper`) | **Adversarial probe** of the running server: `/api;x/…` and `/%61pi/…` bypassed auth | Auth layer must see the same path the router sees. Regression test fails without the fix |
| R10 | Testcontainers for PostgreSQL tests | Same suite pointed at a local PostgreSQL 16 via env vars; CI uses a Postgres service container | Environment had no Docker daemon | Same coverage, no Docker dependency for local runs |
| R11 | Spring Security + OAuth2 for the prototype | Hashed API keys + ownership | Scope decision | Disproportionate for a prototype; the ownership model carries over (D6) |
| R12 | Resolving target hostnames to check reputation/IP | No DNS; static denylist; documented gap | Design review | Latency, dependency, DNS-rebinding bypass (D10) |

## 5. Human sign-off checklist (high-impact changes)

To be completed by the engineer before submission or merge:

| Change | Why high-impact | Sign-off |
|---|---|---|
| `V3__expiry_and_ownership.sql` backfill + NOT NULL | Irreversible data change on existing rows | ☐ |
| `ApiKeyAuthFilter` + path normalisation | Authentication boundary | ☐ |
| Expiry/purge defaults (A-1..A-3) | Data retention; links stop working | ☐ |
| Public API contract (`/api/v1/*`, 410 semantics) | External consumers | ☐ |
| Analytics drop-on-overload policy | Accepted data loss under load | ☐ |

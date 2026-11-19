# Final engineering summary

## 1. Plan and rationale

The work was sequenced so that each step leaves a releasable, green system, and so that each assignment scenario is a real, tagged change to a working codebase:

| Step | Scenario | Why in this order |
|---|---|---|
| `v1-greenfield` | Core service | Smallest useful product: create, redirect, inspect, delete. Defines the error model and test harness everything else reuses |
| `v2-brownfield` | Analytics + bug fix in existing code | Analytics depend on the redirect path existing. Treating v1 as legacy code shows impact analysis and reproduce-first fixing |
| `v3-ambiguous` | "Safe, not forever" | Needs analytics (purge cascades) and auth-aware ownership. Cross-cutting, so it came last |
| gates + review fix | Quality gates, security review | Hardening once behaviour stabilised. The review found and fixed an auth path bypass |

## 2. Artifacts

| Artifact | Location |
|---|---|
| Runnable service (Spring Boot 3.5, Java 21) | `src/main`; `mvn spring-boot:run` or `docker compose up` |
| API definition | OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html` |
| Schema | `src/main/resources/db/migration/V1..V3` |
| Tests (103) | `src/test` → [TESTING.md](TESTING.md) |
| Architecture and decisions | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Scenario write-ups | [scenarios/](scenarios/) |
| AI traceability log | [AI_WORKFLOW.md](AI_WORKFLOW.md) |
| Raw evidence (test output, load runs, probes) | [evidence/](evidence/) |
| CI quality gates | `.github/workflows/ci.yml`, `spotbugs-exclude.xml`, JaCoCo rule in `pom.xml` |

## 3. Quality gates

| Gate | Where | Threshold |
|---|---|---|
| Compile + unit + integration tests | `mvn verify` | 0 failures |
| Coverage | JaCoCo `check` | ≥ 80% lines (actual ~93%) |
| Static analysis + security patterns | SpotBugs + FindSecBugs | 0 findings at Medium+; suppressions need written rationale |
| DB parity | CI job runs the suite on PostgreSQL 16 | 0 failures |
| Dependency / secret / Dockerfile scan | Trivy in CI | No fixable HIGH/CRITICAL |
| Container build | CI | Builds |
| Human sign-off | [AI_WORKFLOW §5](AI_WORKFLOW.md#5-human-sign-off-checklist-high-impact-changes) | Migrations, auth, retention, API contract |

## 4. Risks, trade-offs and failure scenarios

| Failure scenario | Behaviour | Guardrail / evidence |
|---|---|---|
| DB slow or down | Redirects for cached codes keep working ≤ 60 s; others 500 (generic body); health readiness reports DOWN | Problem details hide internals; probes at `/actuator/health/{liveness,readiness}` |
| Analytics write path saturated | Redirects unaffected; events dropped and counted | `analytics.events.dropped`; overload run: 10k/10k redirects 302 |
| Analytics write fails | Logged once per failure + `analytics.events.failed`; redirect already served | `AsyncUncaughtExceptionHandler` |
| Code space collisions | Retry up to 5; then 503 `code-generation-failed` | Unit-tested; length is config |
| Hot link, many concurrent clicks | No lost updates; no write on redirect path | 200/200 concurrency regression test |
| Abusive creator | 429 + Retry-After per client | Integration-tested |
| Scraper / flood from one IP | 429 per IP | Live: 779/1000 limited |
| Phishing / internal-network target | 422 at creation | 37 table-driven policy tests |
| Client probing others' codes | 404 indistinguishable from "doesn't exist" | Integration-tested |
| Auth bypass via path tricks | 401 | Regression test + live probe |
| Bad configuration | Startup fails (validated `@ConfigurationProperties`) | Fail fast |
| Process killed | In-flight analytics in memory lost (≤ queue size); executor drains on graceful shutdown (10 s) | Documented trade-off |

Main trade-offs accepted:
1. **Analytics completeness < redirect availability.** In-memory queue, drop on overflow, eventual consistency (the count converged about 10 s after a 10k burst).
2. **Per-instance state** (rate limits, cache). This is simple and has no new infrastructure, at the cost of approximate limits and ≤ 60 s staleness across nodes.
3. **API keys rather than OAuth2.** Proportionate for a prototype, and the ownership model is reusable.
4. **No reputation lookups.** Static denylist only. Safety of the target is checked at creation time, not continuously.

## 5. Assumptions
* A-1 … A-8 in [scenario 3](scenarios/03-ambiguous.md#assumption-log) (TTL, retention, legacy ownership, limits, safety rules). All are configurable and flagged with the stakeholder question they need.
* Single region, UTC for all date bucketing.
* API clients are systems (per-client keys), not end users.
* H2 is for local and tests only; PostgreSQL is the production target.

## 6. Limitations (honest list)
* CI workflow and Docker image were authored but **not executed here** (no Docker daemon or GitHub runner). Every command in them was run locally instead, including the full suite on a real PostgreSQL 16.
* Load numbers come from one local instance. They are a sanity check, not capacity planning.
* No multi-instance tests; no soak test; no consumer contract tests.
* No admin capabilities: reassigning `legacy` links, disabling a malicious link without deleting it, key rotation endpoints.
* Swagger UI and `/actuator/metrics` are publicly reachable in the default config. Production should disable springdoc and move actuator to an internal management port.

## 7. Next steps (prioritised)
1. Durable analytics pipeline (outbox → Kafka) with batched writes; pre-aggregate before purge if analytics must outlive links.
2. Shared rate limiting and cache invalidation (Redis or gateway).
3. Spring Security + OAuth2 client credentials; keys from a secrets manager.
4. Safe Browsing check at creation + periodic re-scan + "disabled" state.
5. Admin API for legacy-link reassignment and abuse takedown, with an audit trail.

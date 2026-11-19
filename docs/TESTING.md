# Testing approach

## Strategy

| Layer | Purpose | Tooling | Count |
|---|---|---|---|
| Unit (pure logic) | Policies, code generation, token bucket, attribute reduction, service branching | JUnit 5, AssertJ, Mockito, parameterised tables | 80 |
| Integration (in-process HTTP → DB) | Real filters, MVC, validation, JPA, Flyway migrations, async listener | `@SpringBootTest` + MockMvc, Awaitility | 23 |
| Same suite on PostgreSQL 16 | Dialect and migration parity with production DB | env-var datasource override; CI service container | 103 |
| Live end-to-end | Packaged jar on PostgreSQL, real HTTP | `scripts/smoke.sh` | 1 scenario script |
| Performance / overload sanity | Redirect latency, analytics convergence, drop behaviour, rate limits | `scripts/loadtest.py` (stdlib) | 3 runs captured |
| Static and security analysis | Bug patterns + FindSecBugs | SpotBugs (Medium threshold, fails build) | 0 open findings |

Principles applied:
* **Bugs get a failing test first.** The lost-update bug was reproduced against v1 before any fix. The auth path bypass regression test was confirmed to fail with the fix reverted.
* **No sleeps for time.** Expiry uses an injectable `Clock` (`MutableClock`), and token buckets take an injectable nano-clock. Async assertions use Awaitility with a bounded timeout.
* **Tests are independent of DB state.** Aliases are unique per run, so the suite passes repeatedly against a persistent database (this was verified, after it failed once, see AI_WORKFLOW E6).

## Inventory

| Class | Type | Tests | Covers |
|---|---|---|---|
| `UrlPolicyTest` | unit | 37 | schemes, length, malformed, credentials, self-link, denylist + suffix trick, private/loopback/link-local/CGNAT/IPv6 ULA, public IP allowed |
| `ClickAttributesTest` | unit | 16 | referrer reduction, UA family precedence, null/garbage |
| `AliasPolicyTest` | unit | 13 | format, reserved words (case-insensitive) |
| `ShortLinkServiceTest` | unit | 5 | collision retry, give-up, alias conflict, expiry default/bounds |
| `TokenBucketTest` | unit | 4 | burst, refill, cap, Retry-After, per-key isolation |
| `CodeGeneratorTest` | unit | 3 | alphabet/length, uniqueness sample, invalid length |
| `ClickRecorderTest` | unit | 2 | only coarse data stored; deleted-link race |
| `SecurityIntegrationTest` | integration | 8 | 401s, owner isolation (404), path-obfuscation regression, ops endpoints public, bad param 400 |
| `LinkApiIntegrationTest` | integration | 6 | full lifecycle, alias conflict, problem details, validation, malformed JSON, 404 |
| `AnalyticsIntegrationTest` | integration | 4 | **200 concurrent redirects → 200 counted**, stats breakdown, window bounds, 404 |
| `ExpiryIntegrationTest` | integration | 3 | 302 → 410 → retention → purge, default expiry, past expiry 422 |
| `RateLimitIntegrationTest` | integration | 2 | per-client create limit, per-IP redirect limit, Retry-After |

## Results (final commit)

| Gate | Result |
|---|---|
| Tests on H2 | 103 / 103 pass |
| Tests on PostgreSQL 16 | 103 / 103 pass (two consecutive runs on the same DB) |
| Line coverage (JaCoCo, gate ≥ 80%) | ~93% |
| Branch coverage | ~85% |
| SpotBugs + FindSecBugs | 0 open findings (2 fixed, DI false positives justified) |
| Each scenario tag builds on its own | v1: 40 ✔ · v2: 62 ✔ · v3+: 103 ✔ |

Live runs (PostgreSQL 16, single local instance, laptop-class container; numbers are a sanity check, not a benchmark):

| Run | Outcome |
|---|---|
| 10,000 redirects, 16 threads | 100% 302 · ~2,350 req/s · p50 4.5 ms · p95 20 ms · p99 31 ms · click count converged to 10,000 |
| Same, analytics queue forced to 50 | 100% 302 · p99 31 ms · excess events dropped and counted, no redirect impact |
| 1,000 redirects from one IP, default limits | 221 × 302, 779 × 429 |

## How to run

```bash
mvn verify                                   # tests + coverage gate + SpotBugs (H2)

# same suite against PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/shortener \
SPRING_DATASOURCE_USERNAME=shortener SPRING_DATASOURCE_PASSWORD=shortener mvn test

./scripts/smoke.sh                           # against a running instance
APP_RATE_LIMIT_ENABLED=false java -jar target/url-shortener-*.jar &
python3 scripts/loadtest.py 10000 16
```

Reports: `target/site/jacoco/index.html`, `target/surefire-reports/`, `target/spotbugsXml.xml`.

## Not covered (known gaps)
* Multi-instance behaviour (cache staleness across nodes, per-node rate limits). This is reasoned about but not tested.
* Long-running soak and memory profile of the rate-limiter and cache under millions of keys.
* Contract tests for consumers (OpenAPI is generated at `/v3/api-docs` and could seed them).
* The CI workflow and Docker image are written but were not executed in this environment, which had no Docker daemon and no GitHub runner. All the same commands were run locally.

# Scenario 2: Brownfield. Analytics enhancement + concurrency bug fix

**Tag:** `v2-brownfield` · **Result:** bug reproduced (37/200 clicks recorded) → fixed → 62 tests green

## 1. Requirement as received
> Product: "We need click analytics: clicks per day, where traffic comes from, what browsers."
> Ops: "Click counts on popular links look too low."

## 2. Understanding the existing code (codebase reasoning)

Read the v1 code paths before changing anything:

```
GET /{code} → RedirectController → ShortLinkService.resolveAndCount()   @Transactional
                                      ├─ findByCode(code)                 SELECT
                                      ├─ link.setClickCount(n + 1)        in-memory
                                      └─ (commit → Hibernate dirty check) UPDATE ... SET click_count = <n+1>
```

**Hypothesis:** two concurrent redirects both read `n` and both write `n+1`, which is a lost update. Separately, every redirect holds a write transaction and a row lock, so a hot link serialises its own traffic.

**Impact analysis: modules, APIs and data touched**

| Module | Change | Risk |
|---|---|---|
| `link.ShortLinkService` | `resolveAndCount` → read-only `resolve` that publishes an event | Behavioural: count becomes eventually consistent |
| `link.RedirectController` | Pass `Referer` / `User-Agent` through | Low |
| `link.ShortLinkRepository` | Add atomic `incrementClickCount` | Low |
| `link.ShortLink` | Remove `setClickCount` (no callers should mutate it) | Compile-time safety |
| **New** `analytics.*` | Event, recorder, repository, stats service + API | New code, isolated package |
| **New** `config.AsyncConfig` | Bounded executor, drop policy, metrics | Overload behaviour must be explicit |
| DB | `V2__click_event.sql` (additive, FK cascade) | Additive only: no lock on `short_link`, safe online |
| Public API | `GET /api/v1/links/{code}` unchanged; new `/stats` endpoint | Backwards compatible |

## 3. Decomposition

| # | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| B1 | **Reproduce first:** concurrent-redirect test against v1 | — | Fails on v1 (proves the bug) |
| B2 | Atomic increment query | — | Single `UPDATE … SET click_count = click_count + 1` |
| B3 | Event-driven click capture (`LinkClickedEvent`, `@Async` `ClickRecorder`) | B2 | Redirect path is read-only |
| B4 | Bounded executor + drop/failure metrics | B3 | Overload cannot block redirect threads |
| B5 | V2 migration `click_event` + index `(link_id, clicked_on)` | — | Additive; cascades on link delete |
| B6 | Privacy reduction: referrer → host, UA → family, no IP | B5 | Unit-tested classifier |
| B7 | Stats API: zero-filled daily series, top-5 referrers, agents, bounded window | B5 | 1 ≤ days ≤ 90 else 400 |
| B8 | B1 test passes on v2; full suite green | all | 200/200 counted |

## 4. Execution

**B1: the failing test came first.** It was run against the `v1-greenfield` worktree ([evidence](../evidence/v1-lost-update.txt)):

```
[click_count after 200 concurrent redirects]
expected: 200L
 but was: 37L          (a second run: 29L)
```

**Fix.** The redirect transaction became read-only and click capture moved behind an event. The recorder uses the atomic increment, so ordering and concurrency no longer matter.

Design points reviewed by the engineer:
* **Overload policy is explicit.** When the analytics queue is full, events are *dropped and counted*. The alternative, caller-runs, would make redirects pay for analytics under load, which defeats the refactor.
* **Failure isolation.** A first draft wrapped the recorder body in `try/catch` inside `@Transactional`. It was rejected because a swallowed persistence exception still leaves the transaction rollback-only, and the commit then throws from the proxy anyway. Exceptions now go to an `AsyncUncaughtExceptionHandler` that logs and increments `analytics.events.failed` ([AI_WORKFLOW](../AI_WORKFLOW.md) #R4).
* **Early bean initialisation.** Injecting `MeterRegistry` straight into the `AsyncConfigurer` would initialise it before its customisers run. The registry is resolved lazily through `ObjectProvider` (#R5).
* **Stats query shape.** `clicked_on` is denormalised (UTC date) so grouping is index-friendly and portable, avoiding `date_trunc` vs `CAST` dialect differences.

## 5. Validation
| Check | Result |
|---|---|
| B1 regression test (`AnalyticsIntegrationTest.concurrentRedirectsAreAllCounted`) on v2 | 200/200 ✔ |
| Full suite at `v2-brownfield` | 62 tests, 0 failures, coverage gate met |
| Same suite on PostgreSQL 16 (later, final code) | 0 failures |
| Load: 10,000 redirects / 16 threads on PostgreSQL ([evidence](../evidence/loadtest-postgres.txt)) | all 302; p50 4.5 ms, p99 31 ms; count converged to 10,000 within ~10 s |
| Forced overload (queue capacity 50) ([evidence](../evidence/loadtest-overload.txt)) | all 10,000 redirects 302; excess events dropped and counted; no redirect errors |

## 6. Risks, trade-offs and follow-ups
* **Eventual consistency.** `clickCount` lags under load. The load test showed about 2.7k of 10k recorded at the end of the burst, catching up within ~10 s. This is acceptable for analytics and is now documented in the API behaviour.
* **Analytics can be lost** on overload or crash, because the in-memory queue isn't durable. The production path is a durable queue (Kafka/SQS) with batched inserts.
* **Writer throughput.** 2 threads doing 2 statements per event fall behind sustained traffic above ~1k/s. Next steps would be batching (JDBC batch inserts) or more threads.
* **Found later by load test:** logging a WARN per dropped event turned overload into a log flood (8.8k lines in ~7 s). It was removed in the gates commit, and alerting should use the metric instead (#R7).

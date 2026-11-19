# Scenario 1: Greenfield. Core URL shortener service

**Tag:** `v1-greenfield` · **Result:** 40 tests green, coverage gate met

## 1. Requirement as received
> Build a URL shortener service from scratch with core APIs.

## 2. Requirement understanding

Normalised problem statement: *a client submits a long http(s) URL and gets back a short, unique, hard-to-guess code. Anyone requesting `/{code}` is redirected to the original URL. The client can inspect and delete its links.*

| Question raised | Decision for v1 | Revisited in |
|---|---|---|
| Random or sequential codes? | Random Base62 (sequential codes are enumerable) | — |
| Custom aliases? | Yes, optional, validated, must not shadow app paths | — |
| Same URL twice → same code? | No, each create is a new link (separate analytics and owners) | — |
| 301 or 302? | 302 (analytics, revocability) | — |
| Authentication? | Out of scope for v1, flagged as a gap | Scenario 3 |
| Expiry? | Out of scope for v1, flagged | Scenario 3 |
| Analytics? | Only a click counter | Scenario 2 |

## 3. Decomposition

| # | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| T1 | Project skeleton: Boot 3.5, Java 21, Flyway, H2 (PG mode) + Postgres profile | — | App starts; Hibernate validates against the Flyway schema |
| T2 | Schema V1 (`short_link`, unique `code`) | T1 | Portable SQL (H2 + PG) |
| T3 | `CodeGenerator` (SecureRandom Base62) | — | Fixed length, alphabet only, no repeats in 10k samples |
| T4 | `UrlPolicy`: http/https only, host required, ≤ 2048 chars | — | Rejects `javascript:`, `data:`, `file:`, `ftp:`, scheme-less, host-less |
| T5 | `AliasPolicy`: 4–32 `[A-Za-z0-9_-]`, reserved words | — | `api`, `actuator`, `swagger-ui`… rejected (case-insensitive) |
| T6 | `ShortLinkService.create` with collision retry | T2–T5 | Retries on unique violation; gives up after N with 503; alias clash → 409 |
| T7 | REST API: `POST/GET/DELETE /api/v1/links`, `GET /{code}` | T6 | 201 + Location, 302, 204, 404 |
| T8 | Error model: RFC 9457 problem details, no internals leaked | T7 | Stable `type` URIs; generic 500 body |
| T9 | Tests: unit (policies, retry) + MockMvc end-to-end on H2 | T1–T8 | All green |

## 4. Execution notes
* **Transaction boundary on create (engineer decision).** `create()` is deliberately *not* `@Transactional`. A unique-constraint violation inside an outer transaction marks it rollback-only, which would break the retry loop. Each `saveAndFlush` runs in its own repository transaction instead, and `ShortLinkServiceTest.retriesOnCodeCollisionAndSucceeds` covers this.
* **Caught by a test:** `missingUrlFailsBeanValidation` failed because enabling `spring.mvc.problemdetails.enabled` registered Boot's own handler alongside `GlobalExceptionHandler`, and Boot's won for validation errors. The property was removed and one advice owns error mapping (see [AI_WORKFLOW](../AI_WORKFLOW.md) #R2).
* **Known limitation shipped in v1:** the redirect incremented `click_count` with load-modify-save inside the redirect transaction. This is a common MVP pattern, and Scenario 2 treats it as existing code to fix.

## 5. Validation
* `mvn verify` at the `v1-greenfield` tag: **40 tests, 0 failures**, JaCoCo gate met.
* Negative paths covered: invalid URL (422), missing field (400), malformed JSON (400), duplicate alias (409), unknown code (404).

## 6. Risks carried forward
| Risk | Impact | Addressed in |
|---|---|---|
| Unauthenticated management API | Anyone can delete anyone's link | Scenario 3 |
| No rate limiting | Spam-link generation, code enumeration | Scenario 3 |
| Links live forever | Unbounded growth, stale or dangerous targets | Scenario 3 |
| Click counter under concurrency | Lost updates, DB write on every redirect | Scenario 2 |

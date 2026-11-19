# URL Shortener: an AI-assisted engineering exercise

A production-style URL shortener (Spring Boot 3.5 / Java 21) with click analytics, link expiry, API-key ownership, rate limiting and URL safety controls. It was built through three tagged scenarios (greenfield, brownfield, ambiguous), with AI assistance under engineer control.

| Start here | |
|---|---|
| Architecture, components, decisions | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Scenario 1: Greenfield core service | [docs/scenarios/01-greenfield.md](docs/scenarios/01-greenfield.md) |
| Scenario 2: Brownfield analytics + concurrency bug | [docs/scenarios/02-brownfield.md](docs/scenarios/02-brownfield.md) |
| Scenario 3: Ambiguous "safe, not forever" | [docs/scenarios/03-ambiguous.md](docs/scenarios/03-ambiguous.md) |
| AI workflow and traceability (generated / edited / rejected) | [docs/AI_WORKFLOW.md](docs/AI_WORKFLOW.md) |
| Testing approach and results | [docs/TESTING.md](docs/TESTING.md) |
| Engineering summary: risks, assumptions, limitations | [docs/ENGINEERING_SUMMARY.md](docs/ENGINEERING_SUMMARY.md) |

## Setup

**Prerequisites:** JDK 21 and Maven 3.9+. Docker is optional (for PostgreSQL).

```bash
# 1. Build + run all quality gates (tests, coverage >= 80%, SpotBugs/FindSecBugs)
mvn verify

# 2a. Run with zero setup (in-memory H2)
mvn spring-boot:run

# 2b. ...or with PostgreSQL via Docker
docker compose up --build

# 2c. ...or against an existing PostgreSQL
SPRING_PROFILES_ACTIVE=postgres DB_URL=jdbc:postgresql://localhost:5432/shortener \
DB_USER=shortener DB_PASSWORD=shortener java -jar target/url-shortener-1.0.0-SNAPSHOT.jar

# 3. End-to-end smoke test against the running app
./scripts/smoke.sh
```

Swagger UI: http://localhost:8080/swagger-ui.html · Health: http://localhost:8080/actuator/health

The default config ships one **local-only** API key: `demo-key-change-me` (stored as a SHA-256 hash). For any shared environment, replace it:

```bash
echo -n 'your-new-key' | sha256sum        # put the hex digest in config / env:
APP_SECURITY_APIKEYS_0_CLIENTID=my-client APP_SECURITY_APIKEYS_0_SHA256=<digest>
```

## API

| Method & path | Auth | Purpose | Success | Notable errors |
|---|---|---|---|---|
| `POST /api/v1/links` | key | Create (random code or `alias`, optional `expiresAt`) | 201 + `Location` | 401, 409 alias taken, 422 invalid URL/alias/expiry, 429 |
| `GET /api/v1/links/{code}` | key, owner | Metadata incl. `clickCount`, `expiresAt` | 200 | 404 (also for non-owners) |
| `DELETE /api/v1/links/{code}` | key, owner | Delete link and its analytics | 204 | 404 |
| `GET /api/v1/links/{code}/stats?days=7` | key, owner | Daily series, top referrers, browser families | 200 | 400 window, 404 |
| `GET /{code}` | public | Redirect | 302 | 404 unknown, 410 expired, 429 |

All errors are [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` with a stable `type` URI.

```bash
curl -s -X POST localhost:8080/api/v1/links \
  -H 'X-API-Key: demo-key-change-me' -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/very/long/path","alias":"spring-sale","expiresAt":"2027-01-31T00:00:00Z"}'
# {"code":"spring-sale","shortUrl":"http://localhost:8080/spring-sale","targetUrl":"https://example.com/very/long/path",
#  "createdAt":"...","expiresAt":"2027-01-31T00:00:00Z","clickCount":0}

curl -si localhost:8080/spring-sale | head -3      # HTTP/1.1 302 / Location: https://example.com/...
curl -s localhost:8080/api/v1/links/spring-sale/stats?days=7 -H 'X-API-Key: demo-key-change-me'
```

## Configuration (selected)

| Property | Default | Meaning |
|---|---|---|
| `app.base-url` (`APP_BASE_URL`) | `http://localhost:8080` | Used to build `shortUrl` and to block self-links |
| `app.links.default-ttl` / `max-ttl` / `purge-after` | 365d / 730d / 30d | Link lifecycle |
| `app.links.blocked-domains` | `[]` | Domains (and subdomains) that can't be shortened |
| `app.rate-limit.create` / `redirect` | 20 burst, 60/min per client · 200 burst, 1,200/min per IP | Abuse limits (`app.rate-limit.enabled`) |
| `app.analytics.threads` / `queue-capacity` | 2 / 10,000 | Async analytics writer; overflow is dropped and counted |

## Repository map and history

```
src/main/java/com/urlshortener/{link,analytics,security,config,web}
src/main/resources/db/migration/V1..V3      Flyway (portable H2 + PostgreSQL)
src/test/...                                 103 tests (80 unit, 23 integration)
scripts/smoke.sh, scripts/loadtest.py        live verification
docs/                                        design, scenarios, AI log, evidence
.github/workflows/ci.yml                     quality gates
```

```
git log --oneline --decorate
  docs: architecture, scenarios, AI traceability, testing, summary
  fix(security): normalise request path before auth/rate-limit matching
  chore: quality gates, packaging and operability
  (v3-ambiguous)   feat(ambiguous): link expiry, ownership, abuse controls
  (v2-brownfield)  fix+feat(brownfield): async click analytics; fix lost-update race
  (v1-greenfield)  feat: greenfield v1 - create, redirect, inspect and delete
```

Check out any tag to see the system at that scenario. Every tag builds and passes its own tests.

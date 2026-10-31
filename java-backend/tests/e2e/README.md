# E2E Smoke (O-26) — yoCat Office backend

End-to-end **deployment smoke** for the critical user journey, run as an API-layer blackbox
(api-only, no UI) per `docs/test-plan.md` §3.3. This is the post-deploy verification that
proves a *deployed* instance works end to end over real HTTP, real PostgreSQL and real Redis.

## Journey covered (test-plan §3.3 "Smoke = Yes" flows)

```
register → login → GET /auth/me
  → topup (g_big) → spend → buy entitlement (skin_ceo_gold) → GET /entitlements
  → PUT /save → GET /save (cross-device restore)
  → POST /records → GET /records
  → POST /leaderboard → GET /leaderboard
  → overdraw spend → 402 餘額不足 (balance rolled back)
```

## Two interchangeable runners (same journey, same assertions)

| Runner | What it targets | When to use |
|--------|-----------------|-------------|
| `smoke.spec.ts` (Playwright API mode) | a **deployed** instance via `BASE_URL` | after `scripts/local-setup.sh` / in CI post-deploy |
| `apps/api/src/test/java/.../e2e/E2eSmokeJourneyIT.java` (@SpringBootTest full stack) | a **self-booted** app (Testcontainers postgres:16 + redis:7.2) | every build — no external infra; run by Maven failsafe |

The in-process IT is what gates the build (always green with `mvn ... -Dit.test=E2eSmokeJourneyIT`);
this Playwright spec is what you run against a live deployment.

## Run against a deployed instance

```bash
# 1. bring the stack up (k8s-local ingress, default)
./scripts/local-setup.sh                 # adds 127.0.0.1 yocat-office-backend.local to /etc/hosts first

# 2. install + run (from this directory)
cd tests/e2e
npm install
npx playwright install chromium          # API mode needs the runner; no browser is launched

# 3a. default target = k8s ingress host
npx playwright test

# 3b. k8s NodePort
BASE_URL=http://localhost:30088 npx playwright test

# 3c. docker compose
BASE_URL=http://localhost:8080  npx playwright test
```

Failures retain an HTML report + Playwright trace (`npx playwright show-report`).

## Run the in-process equivalent (no deployment needed)

```bash
# from the repo root (java-backend/), real postgres:16 + redis:7.2 via Testcontainers
mvn -pl apps/api -Dit.test=E2eSmokeJourneyIT -Dapi.version=1.43 failsafe:integration-test failsafe:verify
```

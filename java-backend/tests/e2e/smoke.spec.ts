/**
 * E2E deployment smoke (O-26, Harden phase) — Playwright API testing mode.
 *
 * Drives the complete critical user journey (test-plan §3.3 "Smoke=Yes" Critical User Flows) as an
 * API-layer blackbox against a REAL, DEPLOYED application instance — the one brought up by
 * `scripts/local-setup.sh` (k8s-local ingress, default) or `docker compose` — exercising real HTTP,
 * real PostgreSQL and real Redis end to end. No backend is mocked; no DB shortcut is taken; the auth
 * flow (register → login → bearer token) is exercised in full.
 *
 * This is the post-deploy verification counterpart of the in-process `E2eSmokeJourneyIT` (@SpringBootTest
 * full stack) and the strengthened form of local-setup.sh's register→topup→spend→402 smoke.
 *
 * Journey: register → login → GET /auth/me → topup → spend → buy entitlement → GET /entitlements
 *          → PUT /save → GET /save → POST /records → GET /records → POST /leaderboard → GET /leaderboard
 *          → overdraw spend → 402 餘額不足.
 *
 * Target is configurable (deployment-time): BASE_URL env (default = the local-setup.sh k8s ingress host).
 *   BASE_URL=http://localhost:30088   npx playwright test   # k8s NodePort
 *   BASE_URL=http://localhost:8080     npx playwright test   # docker compose
 */
import { test, expect, APIRequestContext } from '@playwright/test';

const BASE_URL = process.env.BASE_URL ?? 'http://yocat-office-backend.local';

function uniqueEmail(): string {
  return `e2e-smoke+${Date.now()}-${Math.floor(Math.random() * 1e6)}@yocat-office-backend.local`;
}

function authHeaders(token: string): Record<string, string> {
  return { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' };
}

test.describe('E2E smoke — full critical user journey (api-only blackbox)', () => {
  test('register → login → topup → spend → buy → save → record → leaderboard → 402', async ({ playwright }) => {
    const api: APIRequestContext = await playwright.request.newContext({ baseURL: BASE_URL });
    const email = uniqueEmail();
    const password = 'Password1!';

    // 0. readiness gate — the app must actually be up
    const ready = await api.get('/health/ready');
    expect(ready.ok(), `service not ready at ${BASE_URL}`).toBeTruthy();

    // 1. register → token + wallet opens at zero (US-MEM-001/AC-1)
    const reg = await api.post('/auth/register', {
      data: { email, displayName: 'E2E Smoke', password },
    });
    expect(reg.status()).toBe(200);
    const regBody = await reg.json();
    expect(regBody.token, 'register must return a token').toBeTruthy();
    expect(regBody.wallet.gems).toBe(0);

    // 2. login → fresh session token (US-MEM-001/AC-4)
    const login = await api.post('/auth/login', { data: { email, password } });
    expect(login.status()).toBe(200);
    const token: string = (await login.json()).token;
    expect(token).toBeTruthy();

    // 3. GET /auth/me authenticated
    const me = await api.get('/auth/me', { headers: authHeaders(token) });
    expect(me.status()).toBe(200);
    expect((await me.json()).account.email).toBe(email);

    // 4. topup g_big → 700 gems (US-WAL-001/AC-1)
    const topup = await api.post('/wallet/topup', { headers: authHeaders(token), data: { pack: 'g_big' } });
    expect(topup.status()).toBe(200);
    expect((await topup.json()).wallet.gems).toBe(700);

    // 5. spend 10 gems → 690 (US-WAL-001/AC-2, server-authoritative)
    const spend = await api.post('/wallet/spend', {
      headers: authHeaders(token),
      data: { currency: 'gems', amount: 10, reason: 'smoke' },
    });
    expect(spend.status()).toBe(200);
    expect((await spend.json()).wallet.gems).toBe(690);

    // 6. buy entitlement skin_ceo_gold for 120 gems → atomic debit+grant, 570 (US-ENT-001/AC-1)
    const buy = await api.post('/entitlements/buy', {
      headers: authHeaders(token),
      data: { kind: 'cosmetic', itemId: 'skin_ceo_gold', currency: 'gems', amount: 120 },
    });
    expect(buy.status()).toBe(200);
    const buyBody = await buy.json();
    expect(buyBody.wallet.gems).toBe(570);
    expect(buyBody.entitlements.map((e: { itemId: string }) => e.itemId)).toContain('skin_ceo_gold');

    // 7. GET /entitlements → owns the item
    const ents = await api.get('/entitlements', { headers: authHeaders(token) });
    expect(ents.status()).toBe(200);
    expect((await ents.json()).map((e: { itemId: string }) => e.itemId)).toContain('skin_ceo_gold');

    // 8. PUT /save → GET /save cross-device restore (US-SAVE-001/AC-5)
    const put = await api.put('/save', {
      headers: authHeaders(token),
      data: { slot: 'main', data: { level: 7, cash: 12345, office: 'hq' } },
    });
    expect(put.status()).toBe(200);
    const getSave = await api.get('/save', { headers: authHeaders(token) });
    expect(getSave.status()).toBe(200);
    expect((await getSave.json()).save.data.level).toBe(7);

    // 9. POST /records → GET /records append-only history
    const addRec = await api.post('/records', {
      headers: authHeaders(token),
      data: { type: 'achievement', payload: { name: 'first_million', value: 1000000 } },
    });
    expect(addRec.status()).toBe(200);
    const recs = await api.get('/records', { headers: authHeaders(token) });
    expect(recs.status()).toBe(200);
    expect((await recs.json()).map((r: { type: string }) => r.type)).toContain('achievement');

    // 10. POST /leaderboard → GET /leaderboard attributed submit + public Top-N
    const score = 98765;
    const submit = await api.post('/leaderboard', {
      headers: authHeaders(token),
      data: { runId: `run-${Date.now()}`, mode: 'sandbox', score, grade: 'S' },
    });
    expect(submit.status()).toBe(200);
    const board = await api.get('/leaderboard?mode=sandbox');
    expect(board.status()).toBe(200);
    expect((await board.json()).map((e: { score: number }) => e.score)).toContain(score);

    // 11. overdraw spend (1,000,000 gems >> 570, within valid [1,10,000,000]) → 402 餘額不足 (US-WAL-001/AC-3)
    const overdraw = await api.post('/wallet/spend', {
      headers: authHeaders(token),
      data: { currency: 'gems', amount: 1000000, reason: 'smoke-overdraw' },
    });
    expect(overdraw.status()).toBe(402);
    const overBody = await overdraw.json();
    expect(overBody.error).toBe('餘額不足');
    expect(overBody.wallet.gems).toBe(570);

    // 12. final reconciliation via /auth/me
    const meFinal = await api.get('/auth/me', { headers: authHeaders(token) });
    expect(meFinal.status()).toBe(200);
    const meBody = await meFinal.json();
    expect(meBody.wallet.gems).toBe(570);
    expect(meBody.entitlements.map((e: { itemId: string }) => e.itemId)).toContain('skin_ceo_gold');

    await api.dispose();
  });
});

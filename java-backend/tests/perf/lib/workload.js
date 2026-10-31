// tests/perf/lib/workload.js
// One realistic high-frequency user iteration touching EVERY SLO class.
// Endpoint set + request DTOs transcribed from docs/API.md §3 Endpoints + §2.7.1
// Bean-Validation schema. Read-heavy mix per PRD §5 高頻場景 (玩家多讀少寫).
//
// Each request carries tags { endpoint, slo } so k6 evaluates the per-class
// thresholds in config.js. Every response is checked against its documented
// success/error set (not a blind 200 check).

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';
import { authHeaders } from './http.js';

// Public reads need no token (API §0 公開端點: GET /leaderboard, GET /deposit/packs, GET /health/*).
export function publicReads() {
  let r = http.get(`${BASE_URL}/health/live`, { tags: { endpoint: 'health_live', slo: 'health' } });
  check(r, { 'health/live 200': (x) => x.status === 200 });

  r = http.get(`${BASE_URL}/health/ready`, { tags: { endpoint: 'health_ready', slo: 'health' } });
  check(r, { 'health/ready 200|503': (x) => x.status === 200 || x.status === 503 });

  r = http.get(`${BASE_URL}/leaderboard?mode=sandbox&limit=20`,
    { tags: { endpoint: 'leaderboard_read', slo: 'leaderboard' } });
  check(r, { 'leaderboard 200': (x) => x.status === 200 });

  r = http.get(`${BASE_URL}/deposit/packs`, { tags: { endpoint: 'deposit_packs', slo: 'read' } });
  check(r, { 'deposit/packs 200': (x) => x.status === 200 });
}

// Authenticated high-frequency journey: reads (me/entitlements/records) + writes
// (topup/spend/buy/records/leaderboard/save). Tolerant of documented business
// errors (402 insufficient balance, 409 already-owned) so load != 5xx noise.
export function authedJourney(token) {
  if (!token) return; // no token (BASE_URL unreachable in setup) -> skip authed calls
  const h = authHeaders(token);

  // ── read class ──
  let r = http.get(`${BASE_URL}/auth/me`, { headers: h, tags: { endpoint: 'auth_me', slo: 'read' } });
  check(r, { 'auth/me 200|401': (x) => x.status === 200 || x.status === 401 });

  r = http.get(`${BASE_URL}/entitlements`, { headers: h, tags: { endpoint: 'entitlements_list', slo: 'read' } });
  check(r, { 'entitlements 200|401': (x) => x.status === 200 || x.status === 401 });

  r = http.get(`${BASE_URL}/records?limit=20`, { headers: h, tags: { endpoint: 'records_list', slo: 'read' } });
  check(r, { 'records 200|401': (x) => x.status === 200 || x.status === 401 });

  // ── write class ── (API §2.7.1 DTO shapes)
  r = http.post(`${BASE_URL}/wallet/topup`, JSON.stringify({ pack: 'g_big' }),
    { headers: h, tags: { endpoint: 'wallet_topup', slo: 'write' } });
  check(r, { 'wallet/topup 200|400|401': (x) => [200, 400, 401].includes(x.status) });

  r = http.post(`${BASE_URL}/wallet/spend`, JSON.stringify({ currency: 'cash', amount: 100, reason: 'buy_cosmetic' }),
    { headers: h, tags: { endpoint: 'wallet_spend', slo: 'write' } });
  check(r, { 'wallet/spend 200|402|401': (x) => [200, 402, 401].includes(x.status) });

  r = http.post(`${BASE_URL}/entitlements/buy`, JSON.stringify({ kind: 'cosmetic', itemId: 'skin_ceo_gold', currency: 'gems', amount: 120 }),
    { headers: h, tags: { endpoint: 'entitlements_buy', slo: 'write' } });
  check(r, { 'entitlements/buy 200|402|409|401': (x) => [200, 402, 409, 401].includes(x.status) });

  r = http.post(`${BASE_URL}/records`, JSON.stringify({ type: 'achievement', payload: { day: 42 } }),
    { headers: h, tags: { endpoint: 'records_write', slo: 'write' } });
  check(r, { 'records write 200|401': (x) => x.status === 200 || x.status === 401 });

  r = http.post(`${BASE_URL}/leaderboard`, JSON.stringify({ runId: `${__VU}-${__ITER}`, mode: 'sandbox', score: 982300, grade: 'S' }),
    { headers: h, tags: { endpoint: 'leaderboard_write', slo: 'write' } });
  check(r, { 'leaderboard write 200|401': (x) => x.status === 200 || x.status === 401 });

  r = http.put(`${BASE_URL}/save`, JSON.stringify({ slot: 'main', data: { level: 3 } }),
    { headers: h, tags: { endpoint: 'save_put', slo: 'write' } });
  check(r, { 'save 200|401': (x) => x.status === 200 || x.status === 401 });
}

// Full iteration used by load / stress / soak.
export function fullIteration(token) {
  publicReads();
  authedJourney(token);
}

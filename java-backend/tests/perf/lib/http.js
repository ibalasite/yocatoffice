// tests/perf/lib/http.js
// Auth + request helpers. Tokens are obtained by exercising the REAL auth flow
// (POST /auth/register + POST /auth/login) — never a fabricated/hardcoded token
// (anti-fake red line). A pre-staged __ENV.TOKEN is honoured when provided.

import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL, TOKEN, RUN_ID } from './config.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

export function authHeaders(token) {
  return { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
}

// Register (idempotently-unique email) then login one real account; return its
// real 32B-hex opaque session token (API §3 member). Re-entrant: the email is
// keyed by RUN_ID + index + timestamp so parallel/repeat runs never collide.
export function provisionUser(idx) {
  const email = `perf+${RUN_ID}_${idx}_${Date.now()}@perf.yocat.test`;
  const password = 'Perf!Pass123'; // satisfies @Size(min=6) PASSWORD_MIN
  const displayName = `perf_${idx}`.slice(0, 24); // @Size(max=24)

  http.post(`${BASE_URL}/auth/register`,
    JSON.stringify({ email, displayName, password }),
    { headers: JSON_HEADERS, tags: { endpoint: 'auth_register', slo: 'write' } });

  const res = http.post(`${BASE_URL}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: JSON_HEADERS, tags: { endpoint: 'auth_login', slo: 'write' } });

  check(res, { 'login -> 200': (r) => r.status === 200 });
  try {
    return res.json('token') || '';
  } catch (_e) {
    return '';
  }
}

// Build a token pool once in setup(). Uses the pre-staged TOKEN for every slot when
// supplied; otherwise provisions `size` real accounts. VUs index the pool by __VU.
export function provisionPool(size) {
  if (TOKEN) {
    return { tokens: new Array(Math.max(1, size)).fill(TOKEN), prestaged: true };
  }
  const tokens = [];
  for (let i = 0; i < Math.max(1, size); i++) {
    tokens.push(provisionUser(i));
  }
  return { tokens, prestaged: false };
}

// Pick this VU's token from the pool (per-VU isolation / re-entrant data access).
export function tokenForVU(pool) {
  if (!pool || !pool.tokens || pool.tokens.length === 0) return '';
  return pool.tokens[(__VU - 1 + pool.tokens.length) % pool.tokens.length];
}

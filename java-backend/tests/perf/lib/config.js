// tests/perf/lib/config.js
// Single source of truth for Base URL + SLO thresholds, shared by every k6 scenario.
//
// SLO numbers are transcribed VERBATIM (no invented values) from:
//   - docs/test-plan.md §3.4 "SLO Targets 表格（與 EDD §10.5 完全對齊）"
//   - docs/test-plan.md §10.2 "SLO / SLI 矩陣（P50/P95/P99，與 EDD §10.5 對齊）"
//   - docs/EDD.md       §10.5 "SLO / SLI / Error Budget"
// Endpoint -> SLO-class mapping from docs/API.md §3 Endpoints + §1.2 BC 歸屬.
//
// All thresholds use abortOnFail:false  => a perf regression is REPORTED (baseline),
// it NEVER blocks the PR / CI (test-plan §3.4 "不 fail PR" + Makefile ci-perf guarded).

// Base URL from env (no hardcoded production host). Default = local bootstrapped API.
export const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/+$/, '');

// Optional pre-staged bearer token (test-plan §3.4 skeleton `--env TOKEN=<token>`).
// When absent, scenarios provision REAL accounts via POST /auth/register + /auth/login.
export const TOKEN = __ENV.TOKEN || '';

// Correlation prefix so concurrent / repeated runs never collide (re-entrancy).
export const RUN_ID = __ENV.RUN_ID || `r${Date.now()}`;

// Where handleSummary() writes the baseline report (relative to k6 cwd = repo root).
export const RESULT_DIR = (__ENV.RESULT_DIR || 'tests/perf/results').replace(/\/+$/, '');

// ── SLO matrix (ms latency; errRate as fraction; tps/rps as req/s) ──
// Source column kept inline for the Review "threshold 來源" check (every number traceable).
export const SLO = {
  // 讀取 API（GET /auth/me, /entitlements, /records）— test-plan §10.2 / §3.4
  read:        { p50: 100, p95: 300, p99: 1000, errRate: 0.001 },
  // 寫入 API（POST /wallet/spend, /topup, /entitlements/buy, /records, PUT /save）
  // test-plan §10.2 + EDD §10.5 寫入 TPS ≥ 200 txn/s
  write:       { p50: 200, p95: 500, p99: 1000, errRate: 0.001, tps: 200 },
  // 排行榜 Top N（GET /leaderboard, limit ≤ 100）— test-plan §10.2 + EDD §10.5 Top N P99 ≤ 300ms
  leaderboard: { p50: 100, p95: 200, p99: 300,  errRate: 0.001 },
  // 健康檢查（GET /health/*）— test-plan §10.2 + §3.4 (Error Rate 0%, ≥ 500 rps)
  health:      { p50: 10,  p95: 20,  p99: 50,   errRate: 0, rps: 500 },
  // 整體系統 @ 500 PCU — test-plan §3.4 + EDD §10.5 Error Rate ≤ 1%
  overall:     { p50: 200, p95: 500, p99: 1000, errRate: 0.01 },
};

function durThresholds(slo) {
  return [
    { threshold: `p(50)<${slo.p50}`, abortOnFail: false },
    { threshold: `p(95)<${slo.p95}`, abortOnFail: false },
    { threshold: `p(99)<${slo.p99}`, abortOnFail: false },
  ];
}

function failThreshold(slo) {
  // errRate 0 (health) -> "rate<=0" (0% allowed); otherwise strict "<fraction".
  const expr = slo.errRate > 0 ? `rate<${slo.errRate}` : 'rate<=0';
  return [{ threshold: expr, abortOnFail: false }];
}

// Build the k6 thresholds map for the given SLO classes.
//   classes          : which per-class SLO sub-metrics to assert (tagged by {slo:<class>})
//   includeThroughput: add write TPS ≥ 200 + health rps ≥ 500 (load/stress/soak only;
//                       meaningless at 1-VU smoke so omitted there)
export function buildThresholds({ classes, includeThroughput = false } = {}) {
  const t = {};
  // Always assert the overall-system SLO.
  t['http_req_duration'] = durThresholds(SLO.overall);
  t['http_req_failed'] = failThreshold(SLO.overall);
  for (const c of classes) {
    const slo = SLO[c];
    if (!slo) continue;
    t[`http_req_duration{slo:${c}}`] = durThresholds(slo);
    t[`http_req_failed{slo:${c}}`] = failThreshold(slo);
    if (includeThroughput && slo.tps) {
      t[`http_reqs{slo:${c}}`] = [{ threshold: `rate>=${slo.tps}`, abortOnFail: false }];
    }
    if (includeThroughput && slo.rps) {
      t[`http_reqs{slo:${c}}`] = [{ threshold: `rate>=${slo.rps}`, abortOnFail: false }];
    }
  }
  return t;
}

// All four SLO classes exercised by the shared workload.
export const ALL_CLASSES = ['read', 'write', 'leaderboard', 'health'];

// Richer percentile breakdown in the end-of-run summary (P50/P90/P95/P99).
export const TREND_STATS = ['avg', 'min', 'med', 'max', 'p(50)', 'p(90)', 'p(95)', 'p(99)'];

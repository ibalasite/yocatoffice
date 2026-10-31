// tests/perf/load-test.js
// Scenario 2 — Normal Load (test-plan §3.4 / §10.1): 500 PCU, 30 min.
//   Ramp-up 5m -> 500 VU, Steady 20m @ 500, Ramp-down 5m -> 0 (test-plan §3.4).
// Pass condition: P99 < 1000ms, P50 < 200ms, Error Rate < 0.1%, write TPS >= 200
//   (test-plan §3.4 / EDD §10.5). Thresholds never block the PR (abortOnFail:false).
//
// Run (full): k6 run --env BASE_URL=https://perf.yocat.internal tests/perf/load-test.js
// CI alias:   make test-perf   /   make ci-perf
//
// Quick parameterised override for CI / smaller envs (keeps the 500-PCU default):
//   --env VUS=500 --env RAMP=5m --env STEADY=20m --env USER_POOL=50

import { sleep } from 'k6';
import { buildThresholds, ALL_CLASSES, TREND_STATS } from './lib/config.js';
import { provisionPool, tokenForVU } from './lib/http.js';
import { fullIteration } from './lib/workload.js';
import { baselineSummary } from './lib/summary.js';

const VUS = parseInt(__ENV.VUS || '500', 10);            // 500 PCU — CONSTANTS §8 啟動期
const RAMP = __ENV.RAMP || '5m';
const STEADY = __ENV.STEADY || '20m';
const USER_POOL = parseInt(__ENV.USER_POOL || '50', 10); // pre-staged real accounts

export const options = {
  scenarios: {
    normal_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP, target: VUS },     // ramp-up to 500 PCU
        { duration: STEADY, target: VUS },   // steady-state
        { duration: RAMP, target: 0 },       // ramp-down
      ],
      tags: { scenario: 'normal_load' },
    },
  },
  thresholds: buildThresholds({ classes: ALL_CLASSES, includeThroughput: true }),
  summaryTrendStats: TREND_STATS,
};

export function setup() {
  return provisionPool(USER_POOL);
}

export default function (pool) {
  fullIteration(tokenForVU(pool));
  sleep(1);
}

export const handleSummary = baselineSummary('load');

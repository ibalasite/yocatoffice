// tests/perf/stress-test.js
// Scenario 3 — Stress / Peak (test-plan §3.4 / §10.1): find the carrying limit
//   + degradation behaviour. From 500 PCU step +25% every 5 min up to 150% peak
//   (750 PCU) x 10 min, then recover. Focus: wallet/spend row-lock contention must
//   stay 0 筆不一致 (reconciliation audited out-of-band via SQL, test-plan §10.3).
// Recovery expectation: system returns to normal <= 5 min after load is removed.
//
// Run: k6 run --env BASE_URL=https://perf.yocat.internal tests/perf/stress-test.js

import { sleep } from 'k6';
import { buildThresholds, ALL_CLASSES, TREND_STATS } from './lib/config.js';
import { provisionPool, tokenForVU } from './lib/http.js';
import { fullIteration } from './lib/workload.js';
import { baselineSummary } from './lib/summary.js';

const BASE = parseInt(__ENV.VUS || '500', 10);  // 500 PCU baseline
const PEAK = Math.round(BASE * 1.5);            // 150% = 750 PCU (test-plan §3.4)
const USER_POOL = parseInt(__ENV.USER_POOL || '75', 10);

export const options = {
  scenarios: {
    stress: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5m', target: BASE },                       // 100% (500)
        { duration: '5m', target: Math.round(BASE * 1.25) },    // +25% (625)
        { duration: '5m', target: PEAK },                       // 150% peak (750)
        { duration: '10m', target: PEAK },                      // hold peak 10m
        { duration: '5m', target: BASE },                       // step back to 100%
        { duration: '5m', target: 0 },                          // recovery window
      ],
      tags: { scenario: 'stress' },
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

export const handleSummary = baselineSummary('stress');

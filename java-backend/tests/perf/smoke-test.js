// tests/perf/smoke-test.js
// Scenario 1 — Smoke Test (test-plan §3.4 / §10.1): 1 VU, 1 min.
// Purpose: confirm the script runs + basic functionality + SLO P99 holds.
// Pass condition: Error Rate = 0%, all SLO P99 met. Runs after every deploy.
//
// Run: k6 run --env BASE_URL=http://localhost:8080 tests/perf/smoke-test.js
// (optionally --env TOKEN=<prestaged>; otherwise a real account is provisioned)

import { sleep } from 'k6';
import { buildThresholds, ALL_CLASSES, TREND_STATS } from './lib/config.js';
import { provisionPool, tokenForVU } from './lib/http.js';
import { fullIteration } from './lib/workload.js';
import { baselineSummary } from './lib/summary.js';

export const options = {
  scenarios: {
    smoke: { executor: 'constant-vus', vus: 1, duration: __ENV.DURATION || '1m', tags: { scenario: 'smoke' } },
  },
  // 1 VU => throughput SLOs (write TPS / health rps) are N/A here, so not asserted.
  thresholds: buildThresholds({ classes: ALL_CLASSES, includeThroughput: false }),
  summaryTrendStats: TREND_STATS,
};

export function setup() {
  return provisionPool(1);
}

export default function (pool) {
  fullIteration(tokenForVU(pool));
  sleep(1);
}

export const handleSummary = baselineSummary('smoke');

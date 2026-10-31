// tests/perf/soak-test.js
// Scenario 4 — Soak / Endurance (test-plan §3.4 / §10.1): 350-400 PCU (正常 70-80%),
//   2 hours. Surfaces memory leaks, HikariCP pool exhaustion, Outbox backlog.
// Monitor out-of-band (test-plan §3.4): JVM Heap/RSS trend, HikariCP utilisation
//   (< 80%), Error-rate trend, GC frequency, trust_outbox_pending (< 100) via
//   /actuator/prometheus. Pass: 2h with no error-rate rise, no linear memory growth,
//   no Outbox backlog.
//
// Run: k6 run --env BASE_URL=https://perf.yocat.internal tests/perf/soak-test.js

import { sleep } from 'k6';
import { buildThresholds, ALL_CLASSES, TREND_STATS } from './lib/config.js';
import { provisionPool, tokenForVU } from './lib/http.js';
import { fullIteration } from './lib/workload.js';
import { baselineSummary } from './lib/summary.js';

const VUS = parseInt(__ENV.VUS || '375', 10);       // 350-400 PCU (test-plan §3.4) -> 375 midpoint
const DURATION = __ENV.DURATION || '2h';            // 2 hours
const USER_POOL = parseInt(__ENV.USER_POOL || '40', 10);

export const options = {
  scenarios: {
    soak: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '5m', target: VUS },       // ramp to soak level
        { duration: DURATION, target: VUS },   // hold 2h
        { duration: '5m', target: 0 },         // ramp-down
      ],
      tags: { scenario: 'soak' },
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

export const handleSummary = baselineSummary('soak');

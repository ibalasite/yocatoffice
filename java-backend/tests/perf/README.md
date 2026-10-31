# Performance Tests (k6)

Load / performance test assets for `yocat-office-backend` (O-27, Phase 9 Harden).
Tool: **k6** (test-plan §3.6 tool matrix · §10.4 "工具版本：k6 latest stable").

## Layout

| File | test-plan scenario | Load profile |
|------|--------------------|--------------|
| `smoke-test.js`  | §3.4 Scenario 1 — Smoke  | 1 VU, 1 min (post-deploy health) |
| `load-test.js`   | §3.4 Scenario 2 — Normal Load | 500 PCU, 5m ramp / 20m steady / 5m down |
| `stress-test.js` | §3.4 Scenario 3 — Stress/Peak | 500 → 750 PCU (150%), step +25%/5m, hold 10m, recover |
| `soak-test.js`   | §3.4 Scenario 4 — Soak | 375 PCU, 2 h |
| `lib/config.js`  | SLO thresholds (single source of truth) | — |
| `lib/http.js`    | real auth provisioning (register+login) | — |
| `lib/workload.js`| shared high-frequency user iteration | — |
| `lib/summary.js` | baseline report writer | — |
| `results/`       | `baseline-<scenario>-<ts>.{json,html}` output | — |

## SLO thresholds (source-traceable)

All numbers in `lib/config.js` are transcribed verbatim from **test-plan §3.4 "SLO Targets 表格"**,
**§10.2 "SLO / SLI 矩陣"** and **EDD §10.5 "SLO / SLI / Error Budget"**:

| SLO class | endpoints | P50 | P95 | P99 | Error rate | Throughput |
|-----------|-----------|-----|-----|-----|-----------|-----------|
| read | `GET /auth/me`,`/entitlements`,`/records`,`/deposit/packs` | ≤100ms | ≤300ms | ≤1000ms | ≤0.1% | — |
| write | `POST /wallet/topup`,`/wallet/spend`,`/entitlements/buy`,`/records`,`/leaderboard`,`PUT /save` | ≤200ms | ≤500ms | ≤1000ms | ≤0.1% | ≥200 txn/s |
| leaderboard | `GET /leaderboard` (Top N, limit ≤100) | ≤100ms | ≤200ms | ≤300ms | ≤0.1% | — |
| health | `GET /health/live`,`/health/ready` | ≤10ms | ≤20ms | ≤50ms | 0% | ≥500 rps |
| overall @500 PCU | all | ≤200ms | ≤500ms | ≤1000ms | ≤1% | — |

Each request is tagged `{ endpoint, slo }`; k6 asserts the per-class thresholds on
tagged sub-metrics (`http_req_duration{slo:write}` etc.). **Every threshold uses
`abortOnFail:false`** — a regression is reported in the baseline, it never fails the
PR/CI (test-plan §3.4 "不 fail PR"; `make ci-perf` is guarded).

## Running

```bash
# install k6 (one of):
scoop install k6            # Windows (user-level)
winget install k6 --id Grafana.k6
# or: docker run --rm -i -v "$PWD:/w" -w /w grafana/k6 run tests/perf/smoke-test.js

# smoke (provisions a real account via POST /auth/register + /auth/login):
k6 run --env BASE_URL=http://localhost:8080 tests/perf/smoke-test.js

# normal load against the Perf env (Prod-like, test-plan §10.4):
k6 run --env BASE_URL=https://perf.yocat.internal tests/perf/load-test.js

# make targets (CI == local):
make test-perf     # k6 run tests/perf/load-test.js   (guarded; no-op if k6 absent)
make ci-perf       # CI alias
```

### Environment variables

| Var | Default | Meaning |
|-----|---------|---------|
| `BASE_URL` | `http://localhost:8080` | target API base URL (no hardcoded prod host) |
| `TOKEN` | *(empty)* | pre-staged bearer token; when set, used instead of provisioning |
| `USER_POOL` | 40–75 | number of real accounts provisioned in `setup()` |
| `RUN_ID` | `r<epoch>` | correlation prefix for provisioned emails (re-entrancy) |
| `RESULT_DIR` | `tests/perf/results` | baseline report output directory |
| `VUS` / `RAMP` / `STEADY` / `DURATION` | scenario defaults | load-profile overrides |

## Baseline report

At end of run `handleSummary()` writes `results/baseline-<scenario>-<ts>.json`
(full metric payload, machine-comparable for SCALE evaluation) and `.html`
(P50/P95/P99 + error-rate + per-threshold PASS/FAIL). No external jslib is fetched
(runs offline).

## Notes

- **Perf environment** (test-plan §10.4): Prod-like, same-spec DB + Redis Sentinel,
  isolated; pre-load ≥ 50,000 accounts + ≥ 1,000,000 `wallet_ledger` rows so
  `idx_ledger_acct` does not regress. The full 500-PCU run belongs on that env, not
  localhost single-process.
- **Reconciliation** (test-plan §10.3): after a wallet stress run, audit
  `SELECT balance FROM wallets` vs `SUM(delta) FROM wallet_ledger` — must be 0 筆不一致.
- **HA / Chaos** perf (kill-pod, rolling-update 5xx < 0.1%) is a k8s-Staging concern
  (test-plan §15.1, EDD §12.2), owned by the HA/chaos sub-step — not these localhost scripts.

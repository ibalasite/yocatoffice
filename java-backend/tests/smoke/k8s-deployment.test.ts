/**
 * tests/smoke/k8s-deployment.test.ts — CI/CD-runnable wrapper around scripts/k8s-smoke.sh.
 *
 * The authoritative smoke logic lives in scripts/k8s-smoke.sh (real `nerdctl build` +
 * `kubectl apply -k` deploy to Rancher Desktop k3s + Flyway Job + rollout + Ingress health
 * + register→topup→spend→402 API smoke + HA pod-kill). This test is the automation entry so
 * `make ci-smoke` / CI pipelines can gate on the deployment smoke the same way they gate on
 * unit/IT tests. It shells out to the one source of truth and asserts exit code 0.
 *
 * Run (Node >= 22, built-in test runner + type-stripping):
 *   node --test --experimental-strip-types tests/smoke/k8s-deployment.test.ts
 * or compiled to JS first. No extra npm dependency is required (node:test + node:child_process).
 *
 * Environment:
 *   KEEP_RESOURCES=1   leave the namespace + schemas up for debugging (default: teardown)
 *   SMOKE_TIMEOUT_MS   override the overall timeout (default 1,200,000 ms = 20 min, first
 *                      build pulls Maven + JRE + Flyway + Redis images).
 */
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(here, '..', '..');
const script = resolve(repoRoot, 'scripts', 'k8s-smoke.sh');
const timeoutMs = Number(process.env.SMOKE_TIMEOUT_MS ?? 1_200_000);

test('scripts/k8s-smoke.sh exists', () => {
  assert.ok(existsSync(script), `missing ${script}`);
});

test('k8s deployment smoke: real deploy to k3s, health 200 + register→topup→spend→402', { timeout: timeoutMs + 60_000 }, () => {
  const res = spawnSync('bash', [script], {
    cwd: repoRoot,
    stdio: 'inherit',
    timeout: timeoutMs,
    env: { ...process.env },
  });
  assert.equal(res.error, undefined, `failed to launch k8s-smoke.sh: ${res.error?.message}`);
  assert.equal(res.status, 0, `k8s-smoke.sh exited with ${res.status} (signal ${res.signal ?? 'none'})`);
});

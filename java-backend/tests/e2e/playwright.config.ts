import { defineConfig } from '@playwright/test';

/**
 * Playwright config for the api-only E2E smoke (test-plan §3.3: Playwright API testing mode, no browser).
 * The target is a REAL deployed instance selected by BASE_URL (default = local-setup.sh k8s ingress host).
 * Failures retain the HTML report + trace (test-plan §Reporting: "E2E 失敗自動存 trace 至 CI artifact").
 */
export default defineConfig({
  testDir: '.',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  retries: process.env.CI ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://yocat-office-backend.local',
    trace: 'retain-on-failure',
  },
});

import { defineConfig, devices } from "@playwright/test";

/**
 * 1회성 신규 시나리오 전용 Playwright 설정.
 * qa/e2e/playwright.config.ts 를 기반으로 testDir 만 현재 디렉토리로 변경.
 */
export default defineConfig({
  testDir: ".",
  testMatch: "*.spec.ts",
  fullyParallel: false,
  forbidOnly: false,
  retries: 0,
  workers: 1,
  reporter: [
    ["list"],
    [
      "json",
      {
        outputFile:
          process.env.QA_REPORT_JSON ??
          "/Users/biuea/sports-application/.analysis/outputs/qa/20260524_full-regression/logs/results-oneshot.json",
      },
    ],
  ],
  use: {
    baseURL: process.env.QA_BASE_URL ?? "http://localhost:3000",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  outputDir:
    process.env.QA_ARTIFACTS_DIR ??
    "/Users/biuea/sports-application/.analysis/outputs/qa/20260524_full-regression/artifacts",
});

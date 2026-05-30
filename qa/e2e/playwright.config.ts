import { defineConfig, devices } from "@playwright/test";

/**
 * QA 회귀 전용 Playwright 설정.
 * web/playwright.config.ts (개발 단계 E2E)와 분리됨.
 *
 * - baseURL: QA_BASE_URL 환경 변수 (기본 localhost:3000)
 * - trace: 실패 시에만 retain (회귀 속도 우선)
 * - reporter: list + json (qa-defect-router가 json 파싱)
 */
export default defineConfig({
  testDir: "./specs",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 2 : undefined,
  reporter: [
    ["list"],
    ["json", { outputFile: process.env.QA_REPORT_JSON ?? "test-results/results.json" }],
    ["html", { outputFolder: "test-results/html", open: "never" }],
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
    {
      name: "mobile-chrome",
      use: { ...devices["Pixel 7"] },
      testMatch: /.*\.mobile\.spec\.ts/,
    },
  ],
  outputDir: process.env.QA_ARTIFACTS_DIR ?? "test-results/artifacts",
});

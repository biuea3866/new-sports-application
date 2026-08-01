import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env["CI"],
  retries: process.env["CI"] ? 2 : 0,
  workers: process.env["CI"] ? 1 : undefined,
  reporter: "html",
  use: {
    baseURL: process.env["BASE_URL"] ?? "http://localhost:3000",
    trace: "on-first-retry",
    /*
     * 한국어 서비스이므로 브라우저 로케일·타임존을 한국으로 고정한다.
     * 네이티브 날짜 입력(`type="date"`·`type="datetime-local"`)의 표시 형식은 문서의
     * `lang` 속성이 아니라 브라우저 로케일을 따르므로, 이 값이 없으면 헤드리스 기본값인
     * en-US가 적용돼 한국어 화면에 `mm/dd/yyyy, --:-- --`가 그대로 노출된다.
     * 타임존까지 고정해야 캡쳐·E2E의 날짜 경계가 KST 기준으로 재현된다.
     */
    locale: "ko-KR",
    timezoneId: "Asia/Seoul",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "next start",
    url: "http://localhost:3000",
    reuseExistingServer: !process.env["CI"],
  },
});

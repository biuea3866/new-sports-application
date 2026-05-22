/**
 * /login 페이지 E2E 테스트 — 실제 풀 플로우 (mock 없음)
 *
 * 실제 BFF(/api/auth/login) → 실제 BE(/auth/login) → access_token 쿠키 →
 * portal layout 인증 통과까지 검증한다. fixture 계정은 qa/e2e/fixtures/seed.sql 에
 * 시드된 qa-portal-fixture@test.local (B2B 3역할 보유, 비밀번호 Passw0rd!).
 *
 * E-01: /login 접근 시 이메일·비밀번호·로그인 버튼이 화면에 표시된다
 * E-02: 유효한 자격증명을 제출하면 실제 로그인 후 /portal로 이동한다
 * E-03: 잘못된 자격증명을 제출하면 에러 메시지가 화면에 표시된다
 * E-04: 비밀번호 필드에서 Enter 키로 제출하면 /portal로 이동한다
 */
import { test, expect } from "@playwright/test";

const FIXTURE_EMAIL = "qa-portal-fixture@test.local";
const FIXTURE_PASSWORD = "Passw0rd!";

test.describe("/login 페이지", () => {
  test("[E-01] /login 접근 시 이메일·비밀번호·로그인 버튼이 화면에 표시된다", async ({
    page,
  }) => {
    await page.goto("/login");

    await expect(page.getByLabel("이메일")).toBeVisible();
    await expect(page.getByLabel("비밀번호")).toBeVisible();
    await expect(page.getByRole("button", { name: "로그인" })).toBeVisible();
  });

  test("[E-02] 유효한 자격증명을 제출하면 실제 로그인 후 /portal로 이동한다", async ({
    page,
  }) => {
    // mock 없음 — 실제 BFF → BE → access_token 쿠키 → portal layout 인증 통과
    await page.goto("/login");

    await page.getByLabel("이메일").fill(FIXTURE_EMAIL);
    await page.getByLabel("비밀번호").fill(FIXTURE_PASSWORD);
    await page.getByRole("button", { name: "로그인" }).click();

    // portal layout 이 access_token 쿠키를 읽어 인증을 통과해야 /portal URL 이 유지된다
    await expect(page).toHaveURL(/\/portal/);
    // 인증 쿠키가 실제로 set 됐는지 검증
    const cookies = await page.context().cookies();
    expect(cookies.some((c) => c.name === "access_token" && c.value.length > 0)).toBe(true);
  });

  test("[E-03] 잘못된 자격증명을 제출하면 에러 메시지가 화면에 표시된다", async ({ page }) => {
    // mock 없음 — 실제 BE 가 잘못된 비밀번호에 401 을 반환
    await page.goto("/login");

    await page.getByLabel("이메일").fill(FIXTURE_EMAIL);
    await page.getByLabel("비밀번호").fill("definitely-wrong-password");
    await page.getByRole("button", { name: "로그인" }).click();

    // 에러 메시지 p 요소 — getByRole("alert") 는 Next.js route-announcer 와
    // 중복 매칭되므로 텍스트로 특정한다
    const errorMessage = page.getByText("이메일 또는 비밀번호가 올바르지 않습니다.");
    await expect(errorMessage).toBeVisible();
    // 로그인 실패 시 /login 에 머문다
    await expect(page).toHaveURL(/\/login/);
  });

  test("[E-04] 비밀번호 필드에서 Enter 키를 누르면 폼이 제출되어 /portal로 이동한다", async ({
    page,
  }) => {
    await page.goto("/login");

    await page.getByLabel("이메일").fill(FIXTURE_EMAIL);
    await page.getByLabel("비밀번호").fill(FIXTURE_PASSWORD);
    await page.getByLabel("비밀번호").press("Enter");

    await expect(page).toHaveURL(/\/portal/);
  });
});

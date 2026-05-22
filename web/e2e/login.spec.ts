/**
 * /login 페이지 E2E 테스트
 *
 * E-01: /login 접근 시 이메일 입력 필드, 비밀번호 입력 필드, 로그인 버튼이 화면에 표시된다
 * E-02: 유효한 자격증명을 입력하고 제출하면 /portal로 이동한다
 * E-03: 잘못된 자격증명을 입력하고 제출하면 에러 메시지가 화면에 표시된다
 * E-04: 폼 필드에서 Enter 키로 제출할 수 있다
 */
import { test, expect } from "@playwright/test";

test.describe("/login 페이지", () => {
  test("[E-01] /login 접근 시 이메일·비밀번호·로그인 버튼이 화면에 표시된다", async ({
    page,
  }) => {
    await page.goto("/login");

    await expect(page.getByLabel("이메일")).toBeVisible();
    await expect(page.getByLabel("비밀번호")).toBeVisible();
    await expect(page.getByRole("button", { name: "로그인" })).toBeVisible();
  });

  test("[E-02] 유효한 자격증명을 제출하면 /portal로 이동한다", async ({ page }) => {
    // BFF /api/auth/login 를 mock하여 성공 응답 반환
    await page.route("/api/auth/login", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ ok: true }),
      });
    });

    // /portal 리다이렉트 목적지도 mock (포털 레이아웃이 인증 체크를 하므로)
    await page.route("/portal", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "text/html",
        body: "<html><body><h1>포털 대시보드</h1></body></html>",
      });
    });

    await page.goto("/login");

    await page.getByLabel("이메일").fill("owner@example.com");
    await page.getByLabel("비밀번호").fill("correct-password");
    await page.getByRole("button", { name: "로그인" }).click();

    await expect(page).toHaveURL(/\/portal/);
  });

  test("[E-03] 잘못된 자격증명을 제출하면 에러 메시지가 화면에 표시된다", async ({ page }) => {
    // BFF /api/auth/login 를 mock하여 401 응답 반환
    await page.route("/api/auth/login", async (route) => {
      await route.fulfill({
        status: 401,
        contentType: "application/json",
        body: JSON.stringify({ message: "로그인이 필요합니다." }),
      });
    });

    await page.goto("/login");

    await page.getByLabel("이메일").fill("owner@example.com");
    await page.getByLabel("비밀번호").fill("wrong-password");
    await page.getByRole("button", { name: "로그인" }).click();

    // 에러 메시지가 화면에 보여야 한다 (role="alert" 요소)
    const errorMessage = page.getByRole("alert");
    await expect(errorMessage).toBeVisible();
    await expect(errorMessage).toContainText("이메일 또는 비밀번호");
  });

  test("[E-04] 비밀번호 필드에서 Enter 키를 누르면 폼이 제출된다", async ({ page }) => {
    await page.route("/api/auth/login", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ ok: true }),
      });
    });

    await page.route("/portal", async (route) => {
      await route.fulfill({
        status: 200,
        contentType: "text/html",
        body: "<html><body><h1>포털 대시보드</h1></body></html>",
      });
    });

    await page.goto("/login");

    await page.getByLabel("이메일").fill("owner@example.com");
    await page.getByLabel("비밀번호").fill("correct-password");
    await page.getByLabel("비밀번호").press("Enter");

    await expect(page).toHaveURL(/\/portal/);
  });
});

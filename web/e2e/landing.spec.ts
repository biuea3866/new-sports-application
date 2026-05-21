import { test, expect } from "@playwright/test";

test.describe("랜딩 페이지", () => {
  test("GET /는 200 응답을 반환하고 앱 이름을 표시한다", async ({ page }) => {
    const response = await page.goto("/");
    expect(response?.status()).toBe(200);

    await expect(page).toHaveTitle(/Sports Application/);
  });

  test("헬스 체크 섹션이 존재한다", async ({ page }) => {
    await page.goto("/");
    const healthSection = page.getByRole("region", { name: "헬스 체크" });
    await expect(healthSection).toBeVisible();
  });
});

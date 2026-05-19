/**
 * [S-01] a11y 검증 — Playwright + axe-core
 *
 * 랜딩 페이지: axe-core 위반 0건
 *
 * 다른 페이지(시설, 경기, 장바구니, 마이)는 WEB-02/03/06 구현 후 추가 예정
 * TODO: 시설 페이지 — WEB-03 구현 후 활성화
 * TODO: 경기 페이지 — WEB-03 구현 후 활성화
 * TODO: 장바구니 페이지 — WEB-04 구현 후 활성화
 * TODO: 마이 페이지 — WEB-07a~d 구현 후 활성화
 */
import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test.describe("[S-01] 랜딩 페이지 접근성", () => {
  test("axe-core 위반이 0건이다", async ({ page }) => {
    await page.goto("/");

    const accessibilityScanResults = await new AxeBuilder({ page }).analyze();

    expect(accessibilityScanResults.violations).toEqual([]);
  });

  test("lang 속성이 설정되어 있다", async ({ page }) => {
    await page.goto("/");
    const lang = await page.getAttribute("html", "lang");
    expect(lang).toBeTruthy();
  });
});

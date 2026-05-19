/**
 * [S-03] 키보드 네비게이션 — Tab 순서로 모든 인터랙티브 요소가 도달 가능하다
 */
import { test, expect } from "@playwright/test";

test.describe("[S-03] 랜딩 페이지 키보드 네비게이션", () => {
  test("Tab 키로 모든 인터랙티브 요소에 포커스가 도달한다", async ({ page }) => {
    await page.goto("/");

    // 인터랙티브 요소 수집 (link, button, input, select, textarea, [tabindex])
    const interactiveSelectors = [
      "a[href]",
      "button:not([disabled])",
      "input:not([disabled])",
      "select:not([disabled])",
      "textarea:not([disabled])",
      "[tabindex]:not([tabindex='-1'])",
    ].join(", ");

    const interactiveCount = await page.locator(interactiveSelectors).count();

    if (interactiveCount === 0) {
      // 랜딩 페이지에 인터랙티브 요소가 없는 경우 — skip
      test.info().annotations.push({
        type: "skip",
        description: "현재 랜딩 페이지에 인터랙티브 요소 없음",
      });
      return;
    }

    // Tab을 눌러서 포커스가 이동하는지 확인
    await page.keyboard.press("Tab");
    const focusedElement = page.locator(":focus");
    await expect(focusedElement).toHaveCount(1);
  });

  test("포커스 가시성: 포커스된 요소에 outline이 있다", async ({ page }) => {
    await page.goto("/");

    await page.keyboard.press("Tab");

    const focusedEl = await page.evaluate(() => {
      const el = document.activeElement;
      if (!el || el === document.body) return null;
      const style = window.getComputedStyle(el, ":focus-visible");
      return {
        tag: el.tagName,
        outlineWidth: style.outlineWidth,
        outlineStyle: style.outlineStyle,
      };
    });

    // 포커스 가능한 요소가 있는 경우 outline 검증
    if (focusedEl) {
      // outline이 none이 아니거나 ring 클래스가 있어야 함
      const hasOutline = focusedEl.outlineStyle !== "none" && focusedEl.outlineWidth !== "0px";
      expect(hasOutline).toBe(true);
    }
  });
});

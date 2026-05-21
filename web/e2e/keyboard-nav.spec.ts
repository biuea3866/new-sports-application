/**
 * [S-03] 키보드 네비게이션 — Tab 순서로 모든 인터랙티브 요소가 도달 가능하다
 */
import { test, expect } from "@playwright/test";

const INTERACTIVE_SELECTORS = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
];

test.describe("[S-03] 랜딩 페이지 키보드 네비게이션", () => {
  test("Tab 키로 모든 인터랙티브 요소에 순차 도달한다", async ({ page }) => {
    await page.goto("/");

    const selectorList = INTERACTIVE_SELECTORS.join(", ");
    const interactiveCount = await page.locator(selectorList).count();

    test.skip(
      interactiveCount === 0,
      "현재 랜딩 페이지에 인터랙티브 요소가 없음 — 후속 페이지(WEB-02 등) 추가 시 동작"
    );

    // 페이지의 모든 인터랙티브 요소에 식별자 부여 (focus 추적용)
    const targetIdentities = await page.evaluate((selList) => {
      const els = Array.from(document.querySelectorAll<HTMLElement>(selList));
      return els.map((el, i) => {
        if (!el.id) {
          el.setAttribute("data-kbnav-id", `kbnav-${i}`);
        }
        return el.id || `kbnav-${i}`;
      });
    }, selectorList);

    expect(targetIdentities.length).toBe(interactiveCount);

    // body 에서 시작해서 Tab 을 N+1 회 눌러도 모든 인터랙티브 요소를 한 번씩은 통과해야 한다.
    // (브라우저 chrome / 주소창 focus 이슈 회피를 위해 처음에 body 클릭)
    await page.locator("body").click();

    const focusedIdentities = new Set<string>();
    const maxAttempts = interactiveCount * 2 + 2;
    for (let i = 0; i < maxAttempts; i++) {
      await page.keyboard.press("Tab");
      const id = await page.evaluate(() => {
        const el = document.activeElement as HTMLElement | null;
        if (!el || el === document.body) return null;
        return el.id || el.getAttribute("data-kbnav-id") || null;
      });
      if (id !== null) {
        focusedIdentities.add(id);
      }
      if (focusedIdentities.size >= interactiveCount) {
        break;
      }
    }

    // 모든 인터랙티브 요소가 Tab 순회 중 한 번 이상 포커스를 받았어야 한다.
    for (const expected of targetIdentities) {
      expect(focusedIdentities.has(expected)).toBe(true);
    }
  });

  test("포커스 가시성: 포커스된 요소에 outline 이 있다", async ({ page }) => {
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

    if (focusedEl) {
      const hasOutline = focusedEl.outlineStyle !== "none" && focusedEl.outlineWidth !== "0px";
      expect(hasOutline).toBe(true);
    }
  });
});

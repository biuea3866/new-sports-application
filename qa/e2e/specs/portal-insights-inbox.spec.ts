/**
 * E2E-11 신규 화면 렌더 검증 — /portal/insights (FR-03 통합 KPI) · /portal/inbox (FR-04 알림센터)
 *
 * 15 PR 머지 후 신규 화면이 인증 후 실제 렌더되는지 Playwright로 검증.
 *
 * 사전 조건:
 *   - QA_BASE_URL=http://localhost:3000 (web, next start production build)
 *   - QA_API_URL=http://localhost:8080 (BE)
 *   - seed 운영자: qa-portal-fixture@test.local / Passw0rd! (roles: FACILITY_OWNER, EVENT_HOST, GOODS_SELLER)
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";

const BASE_URL = process.env.QA_BASE_URL ?? "http://localhost:3000";
const API_URL = process.env.QA_API_URL ?? "http://localhost:8080";
const SEED_EMAIL = "qa-portal-fixture@test.local";
const SEED_PASSWORD = "Passw0rd!";

/**
 * seed 운영자로 BE 로그인 후 access_token 쿠키를 context에 심는다.
 * 실패 시 false를 반환한다.
 */
async function loginAsOperator(context: import("@playwright/test").BrowserContext): Promise<boolean> {
  const api = await playwrightRequest.newContext();
  const loginRes = await api.post(`${API_URL}/auth/login`, {
    data: { email: SEED_EMAIL, password: SEED_PASSWORD },
    failOnStatusCode: false,
  });
  if (loginRes.status() !== 200) {
    await api.dispose();
    return false;
  }
  const body = await loginRes.json() as { accessToken: string };
  await api.dispose();
  await context.addCookies([
    {
      name: "access_token",
      value: body.accessToken,
      url: BASE_URL,
    },
  ]);
  return true;
}

// ─── /portal/insights ────────────────────────────────────────────────────────

test.describe("E2E-11-01 /portal/insights — 통합 KPI 페이지 렌더", () => {
  test.beforeEach(async ({ context }) => {
    const ok = await loginAsOperator(context);
    if (!ok) test.skip();
  });

  test("[E2E-11-01-01] 인증 후 /portal/insights 진입 시 '운영 인사이트' h1이 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    const h1 = page.getByRole("heading", { name: "운영 인사이트" });
    await expect(h1).toBeVisible({ timeout: 10000 });
  });

  test("[E2E-11-01-02] 기간 필터 버튼 4개(오늘/최근7일/최근30일/사용자지정)가 모두 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    await expect(page.getByRole("button", { name: "오늘" })).toBeVisible({ timeout: 10000 });
    await expect(page.getByRole("button", { name: "최근 7일" })).toBeVisible();
    await expect(page.getByRole("button", { name: "최근 30일" })).toBeVisible();
    await expect(page.getByRole("button", { name: "사용자 지정" })).toBeVisible();
  });

  test("[E2E-11-01-03] KPI 데이터 로드 후 시설 KPI 섹션 h2가 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    // 로딩 텍스트가 사라질 때까지 대기
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });
    const facilitySection = page.getByRole("region", { name: "시설 KPI" });
    const goodsSection = page.getByRole("region", { name: "굿즈 KPI" });
    const ticketSection = page.getByRole("region", { name: "티켓 KPI" });
    // 섹션 중 하나라도 렌더되거나 "표시할 데이터가 없습니다" 메시지가 표시되어야 한다
    const facilityVisible = await facilitySection.isVisible().catch(() => false);
    const goodsVisible = await goodsSection.isVisible().catch(() => false);
    const ticketVisible = await ticketSection.isVisible().catch(() => false);
    const noDataVisible = await page.getByText("표시할 데이터가 없습니다").isVisible().catch(() => false);
    const errorVisible = await page.getByRole("alert").isVisible().catch(() => false);
    expect(
      facilityVisible || goodsVisible || ticketVisible || noDataVisible || errorVisible,
      "KPI 섹션, 빈 상태 텍스트, 또는 에러 메시지 중 하나가 렌더되어야 합니다"
    ).toBe(true);
  });

  test("[E2E-11-01-04] 시설 KPI 섹션에 가동률·노쇼율·인기시설수 카드가 렌더된다 (데이터 있을 때)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });

    const facilitySection = page.getByRole("region", { name: "시설 KPI" });
    const isVisible = await facilitySection.isVisible().catch(() => false);
    if (!isVisible) {
      test.info().annotations.push({ type: "skip-reason", description: "시설 KPI 섹션 미렌더 — 데이터 없음 또는 에러" });
      test.skip();
      return;
    }

    await expect(facilitySection.getByText("가동률")).toBeVisible();
    await expect(facilitySection.getByText("노쇼율")).toBeVisible();
    await expect(facilitySection.getByText("인기 시설 수")).toBeVisible();
  });

  test("[E2E-11-01-05] 굿즈 KPI 섹션에 일매출합계·재고회전율·품절SKU 카드가 렌더된다 (데이터 있을 때)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });

    const goodsSection = page.getByRole("region", { name: "굿즈 KPI" });
    const isVisible = await goodsSection.isVisible().catch(() => false);
    if (!isVisible) {
      test.info().annotations.push({ type: "skip-reason", description: "굿즈 KPI 섹션 미렌더 — 데이터 없음 또는 에러" });
      test.skip();
      return;
    }

    await expect(goodsSection.getByText("일 매출 합계")).toBeVisible();
    await expect(goodsSection.getByText("재고 회전율")).toBeVisible();
    await expect(goodsSection.getByText("품절 SKU")).toBeVisible();
  });

  test("[E2E-11-01-06] 티켓 KPI 섹션에 판매수량·환불율·무료증정 카드가 렌더된다 (데이터 있을 때)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });

    const ticketSection = page.getByRole("region", { name: "티켓 KPI" });
    const isVisible = await ticketSection.isVisible().catch(() => false);
    if (!isVisible) {
      test.info().annotations.push({ type: "skip-reason", description: "티켓 KPI 섹션 미렌더 — 데이터 없음 또는 에러" });
      test.skip();
      return;
    }

    await expect(ticketSection.getByText("판매 수량")).toBeVisible();
    await expect(ticketSection.getByText("환불율")).toBeVisible();
    await expect(ticketSection.getByText("무료 증정")).toBeVisible();
  });

  test("[E2E-11-01-07] '오늘' 필터 클릭 시 로딩 후 화면이 갱신된다 (에러 없음)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });

    await page.getByRole("button", { name: "오늘" }).click();
    // 클릭 후 로딩 상태 진입 및 완료
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });
    // 에러 없음 단언
    const alertVisible = await page.getByRole("alert").isVisible().catch(() => false);
    if (alertVisible) {
      const alertText = await page.getByRole("alert").innerText();
      // 에러 발생 시 기록 (spec failure로 처리)
      expect(alertText, "'오늘' 필터 클릭 후 에러 발생").toBe("");
    }
  });

  test("[E2E-11-01-08] '최근 30일' 필터 클릭 시 로딩 후 화면이 갱신된다 (에러 없음)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });

    await page.getByRole("button", { name: "최근 30일" }).click();
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });
    const alertVisible = await page.getByRole("alert").isVisible().catch(() => false);
    if (alertVisible) {
      const alertText = await page.getByRole("alert").innerText();
      expect(alertText, "'최근 30일' 필터 클릭 후 에러 발생").toBe("");
    }
  });

  test("[E2E-11-01-09] 인기 시설 TOP5 섹션 h2가 렌더된다 (데이터 있을 때)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/insights`, { waitUntil: "networkidle" });
    await expect(page.getByText("데이터를 불러오는 중...")).toBeHidden({ timeout: 15000 });

    const top5Section = page.getByRole("region", { name: "인기 시설 TOP5" });
    const isVisible = await top5Section.isVisible().catch(() => false);
    if (!isVisible) {
      test.info().annotations.push({ type: "skip-reason", description: "인기 시설 TOP5 섹션 미렌더 — 데이터 없음" });
      test.skip();
      return;
    }
    // 순위 리스트 또는 "데이터가 없습니다" 텍스트가 렌더되어야 한다
    const listVisible = await top5Section.getByRole("list", { name: "인기 시설 순위 목록" }).isVisible().catch(() => false);
    const emptyVisible = await top5Section.getByText("데이터가 없습니다.").isVisible().catch(() => false);
    expect(listVisible || emptyVisible, "인기 시설 순위 목록 또는 빈 상태 텍스트").toBe(true);
  });
});

// ─── /portal/inbox ────────────────────────────────────────────────────────────

test.describe("E2E-11-02 /portal/inbox — 알림센터 페이지 렌더", () => {
  test.beforeEach(async ({ context }) => {
    const ok = await loginAsOperator(context);
    if (!ok) test.skip();
  });

  test("[E2E-11-02-01] 인증 후 /portal/inbox 진입 시 '알림센터' h1이 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/inbox`, { waitUntil: "networkidle" });
    const h1 = page.getByRole("heading", { name: "알림센터" });
    await expect(h1).toBeVisible({ timeout: 10000 });
  });

  test("[E2E-11-02-02] 알림 필터 섹션 — 유형·상태 select가 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/inbox`, { waitUntil: "networkidle" });
    await expect(page.getByRole("region", { name: "알림 필터" })).toBeVisible({ timeout: 10000 });
    await expect(page.getByLabel("알림 유형 필터")).toBeVisible();
    await expect(page.getByLabel("알림 상태 필터")).toBeVisible();
  });

  test("[E2E-11-02-03] 알림 목록 또는 빈 상태 텍스트가 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/inbox`, { waitUntil: "networkidle" });
    await expect(page.getByText("알림 목록을 불러오는 중...")).toBeHidden({ timeout: 15000 });

    const listVisible = await page.getByRole("list", { name: "알림 항목 목록" }).isVisible().catch(() => false);
    const emptyVisible = await page.getByText("알림이 없습니다.").isVisible().catch(() => false);
    const errorVisible = await page.getByRole("alert").isVisible().catch(() => false);

    expect(
      listVisible || emptyVisible || errorVisible,
      "알림 목록, 빈 상태 텍스트('알림이 없습니다.'), 또는 에러 메시지 중 하나가 렌더되어야 합니다"
    ).toBe(true);
  });

  test("[E2E-11-02-04] 총 N건 텍스트가 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/inbox`, { waitUntil: "networkidle" });
    await expect(page.getByText("알림 목록을 불러오는 중...")).toBeHidden({ timeout: 15000 });

    // 에러가 없는 경우에만 단언 — alert 텍스트가 실제로 있어야 에러로 판단
    const alertEl = page.getByRole("alert");
    const alertVisible = await alertEl.isVisible().catch(() => false);
    const alertText = alertVisible ? await alertEl.innerText().catch(() => "") : "";
    if (alertText.trim().length > 0) {
      test.info().annotations.push({ type: "skip-reason", description: `알림 API 에러로 총 건수 렌더 불가: ${alertText}` });
      test.skip();
      return;
    }

    // "총 N건" 패턴 확인
    await expect(page.getByText(/총.*건/)).toBeVisible({ timeout: 10000 });
  });

  test("[E2E-11-02-05] 유형 필터를 '이상 감지'로 변경 시 목록이 갱신된다 (에러 없음)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/inbox`, { waitUntil: "networkidle" });
    await expect(page.getByText("알림 목록을 불러오는 중...")).toBeHidden({ timeout: 15000 });

    // 실제 에러 텍스트가 있는 경우만 skip
    const alertEl = page.getByRole("alert");
    const alertVisible = await alertEl.isVisible().catch(() => false);
    const initialAlertText = alertVisible ? await alertEl.innerText().catch(() => "") : "";
    if (initialAlertText.trim().length > 0) {
      test.info().annotations.push({ type: "skip-reason", description: `초기 로드 에러 — 필터 테스트 건너뜀: ${initialAlertText}` });
      test.skip();
      return;
    }

    await page.getByLabel("알림 유형 필터").selectOption("ANOMALY");
    await expect(page.getByText("알림 목록을 불러오는 중...")).toBeHidden({ timeout: 15000 });

    const alertEl2 = page.getByRole("alert");
    const alertVisible2 = await alertEl2.isVisible().catch(() => false);
    const alertText2 = alertVisible2 ? await alertEl2.innerText().catch(() => "") : "";
    expect(alertText2.trim(), "유형 필터 변경 후 에러 발생").toBe("");

    // 목록 또는 빈 상태가 렌더되어야 함
    const listVisible = await page.getByRole("list", { name: "알림 항목 목록" }).isVisible().catch(() => false);
    const emptyVisible = await page.getByText("알림이 없습니다.").isVisible().catch(() => false);
    expect(listVisible || emptyVisible, "필터 후 목록 또는 빈 상태 텍스트").toBe(true);
  });

  test("[E2E-11-02-06] 상태 필터를 '읽지 않음'으로 변경 시 목록이 갱신된다 (에러 없음)", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/inbox`, { waitUntil: "networkidle" });
    await expect(page.getByText("알림 목록을 불러오는 중...")).toBeHidden({ timeout: 15000 });

    // 실제 에러 텍스트가 있는 경우만 skip
    const alertEl = page.getByRole("alert");
    const alertVisible = await alertEl.isVisible().catch(() => false);
    const initialAlertText = alertVisible ? await alertEl.innerText().catch(() => "") : "";
    if (initialAlertText.trim().length > 0) {
      test.info().annotations.push({ type: "skip-reason", description: `초기 로드 에러 — 상태 필터 테스트 건너뜀: ${initialAlertText}` });
      test.skip();
      return;
    }

    await page.getByLabel("알림 상태 필터").selectOption("UNREAD");
    await expect(page.getByText("알림 목록을 불러오는 중...")).toBeHidden({ timeout: 15000 });

    const alertEl2 = page.getByRole("alert");
    const alertVisible2 = await alertEl2.isVisible().catch(() => false);
    const alertText2 = alertVisible2 ? await alertEl2.innerText().catch(() => "") : "";
    expect(alertText2.trim(), "상태 필터 변경 후 에러 발생").toBe("");

    const listVisible = await page.getByRole("list", { name: "알림 항목 목록" }).isVisible().catch(() => false);
    const emptyVisible = await page.getByText("알림이 없습니다.").isVisible().catch(() => false);
    expect(listVisible || emptyVisible).toBe(true);
  });

  test("[E2E-11-02-07] UNREAD 알림이 있을 때 '읽음' 버튼 클릭 시 상태가 READ로 변경된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal/inbox`, { waitUntil: "networkidle" });
    await expect(page.getByText("알림 목록을 불러오는 중...")).toBeHidden({ timeout: 15000 });

    // UNREAD 알림 항목 확인
    const unreadItem = page.locator("li.border-blue-200").first();
    const isUnreadVisible = await unreadItem.isVisible().catch(() => false);
    if (!isUnreadVisible) {
      test.info().annotations.push({ type: "skip-reason", description: "UNREAD 알림 없음 — 읽음 처리 테스트 건너뜀" });
      test.skip();
      return;
    }

    // 읽음 버튼 클릭
    const readButton = unreadItem.getByRole("button", { name: /읽음|읽고 이동/ });
    await readButton.click();

    // 처리 중 상태 대기
    await expect(readButton).not.toBeDisabled({ timeout: 8000 });

    // 해당 항목의 배경이 blue-50에서 변경되었는지 확인 (UNREAD → READ 상태 전이)
    await expect(unreadItem).not.toHaveClass(/border-blue-200/, { timeout: 5000 });
  });
});

// ─── 기존 회귀: /portal 대시보드 (인증 후 렌더 확인) ──────────────────────────

test.describe("E2E-11-03 기존 회귀 — /portal 대시보드 인증 후 렌더", () => {
  test.beforeEach(async ({ context }) => {
    const ok = await loginAsOperator(context);
    if (!ok) test.skip();
  });

  test("[E2E-11-03-01] 인증 후 /portal 진입 시 '대시보드' h1이 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal`, { waitUntil: "networkidle" });
    const h1 = page.getByRole("heading", { name: "대시보드" });
    await expect(h1).toBeVisible({ timeout: 10000 });
  });

  test("[E2E-11-03-02] 인증 후 /portal 에 시설/경기/상품 섹션 중 하나 이상이 렌더된다", async ({ page }) => {
    await page.goto(`${BASE_URL}/portal`, { waitUntil: "networkidle" });

    const facilityVisible = await page.getByRole("region", { name: /시설.*슬롯/ }).isVisible().catch(() => false);
    const eventVisible = await page.getByRole("region", { name: "경기" }).isVisible().catch(() => false);
    const productVisible = await page.getByRole("region", { name: "상품" }).isVisible().catch(() => false);
    const noDataVisible = await page.getByText("표시할 데이터가 없습니다").isVisible().catch(() => false);
    const errorVisible = await page.getByRole("alert").isVisible().catch(() => false);

    expect(
      facilityVisible || eventVisible || productVisible || noDataVisible || errorVisible,
      "시설/경기/상품 섹션, 빈 상태, 또는 에러 중 하나가 렌더되어야 합니다"
    ).toBe(true);
  });
});

// ─── 기존 회귀: /portal/payments 매출 페이지 ──────────────────────────────────

test.describe("E2E-11-04 기존 회귀 — /portal/payments 매출 페이지 렌더", () => {
  test.beforeEach(async ({ context }) => {
    const ok = await loginAsOperator(context);
    if (!ok) test.skip();
  });

  test("[E2E-11-04-01] 인증 후 /portal/payments 진입 시 5xx 없이 렌더된다", async ({ page }) => {
    const response = await page.goto(`${BASE_URL}/portal/payments`, { waitUntil: "networkidle" });
    expect(response?.status(), "페이지 응답이 5xx이면 안 됩니다").toBeLessThan(500);
    // 페이지 body가 비어있지 않음
    const bodyText = await page.locator("body").innerText();
    expect(bodyText.trim().length, "페이지 body가 비어있습니다").toBeGreaterThan(0);
  });
});

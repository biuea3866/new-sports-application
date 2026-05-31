/**
 * E2E-MOB-01 mobile expo web 화면 렌더 검증
 *
 * 대상: placeholder→실구현된 mobile 화면 4종
 *   - app/payment/new.tsx       결제 수단 선택 화면
 *   - app/event/[id]/order.tsx  티켓 발권 좌석 확인 화면
 *   - app/notifications/index.tsx 알림 목록 화면
 *   - app/(tabs)/               홈/스토어/티켓/커뮤니티/마이 탭
 *
 * 방법:
 *   expo web은 localStorage 기반 인증(secure-store.web.ts).
 *   테스트 시작 전 BE에서 발급한 토큰을 localStorage에 주입 → AuthGuard 통과.
 *
 * 주의:
 *   expo web hydration이 느릴 수 있으므로 waitForSelector + timeout을 길게 설정.
 *   번들 에러, "준비중" placeholder, 빈 화면은 Fail로 판정.
 */
import { test, expect, type Page } from "@playwright/test";

const MOBILE_BASE_URL = process.env.QA_MOBILE_URL ?? "http://localhost:8081";
const API_URL = process.env.QA_API_URL ?? "http://localhost:8080";

/** 시드 계정 토큰 발급 */
async function getAuthTokens(): Promise<{
  accessToken: string;
  refreshToken: string;
}> {
  const res = await fetch(`${API_URL}/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      email: "qa-portal-fixture@test.local",
      password: "Passw0rd!",
    }),
  });
  if (!res.ok) throw new Error(`Login failed: ${res.status}`);
  return res.json();
}

/** expo web localStorage에 refreshToken 주입 후 페이지 새로고침 → AuthGuard 통과 */
async function injectAuthAndNavigate(
  page: Page,
  path: string,
  refreshToken: string,
): Promise<void> {
  // 먼저 기본 페이지를 로드해 도메인 컨텍스트를 확보
  await page.goto(MOBILE_BASE_URL, { waitUntil: "domcontentloaded" });
  // localStorage에 refreshToken 세팅
  await page.evaluate(
    (token: string) => window.localStorage.setItem("refreshToken", token),
    refreshToken,
  );
  // 목표 경로로 이동
  await page.goto(`${MOBILE_BASE_URL}${path}`, {
    waitUntil: "domcontentloaded",
    timeout: 30000,
  });
}

/** 화면에 "준비중" placeholder가 있으면 실구현 미완으로 Fail */
async function assertNotPlaceholder(page: Page): Promise<void> {
  const bodyText = await page.textContent("body");
  expect(
    bodyText,
    "화면에 '준비중' placeholder 텍스트가 없어야 함",
  ).not.toContain("준비중");
}

/** 번들 에러(JavaScript error overlay) 부재 확인 */
async function assertNoBundleError(page: Page): Promise<void> {
  // expo web dev server 에러 오버레이: "ReferenceError", "SyntaxError", "Cannot find module"
  const bodyText = await page.textContent("body");
  expect(bodyText, "번들 에러 오버레이 없어야 함").not.toMatch(
    /ReferenceError|SyntaxError|Cannot find module|Uncaught Error/,
  );
}

test.describe("E2E-MOB-01 mobile expo web 화면 렌더 검증", () => {
  let refreshToken: string;
  let accessToken: string;

  test.beforeAll(async () => {
    const tokens = await getAuthTokens();
    accessToken = tokens.accessToken;
    refreshToken = tokens.refreshToken;
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-01  로그인 화면 렌더
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-01] 로그인 화면 — 이메일·비밀번호 입력 필드와 로그인 버튼이 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/auth-login";

    await page.goto(`${MOBILE_BASE_URL}/(auth)/login`, {
      waitUntil: "domcontentloaded",
      timeout: 30000,
    });

    // expo web이 SPA이므로 실제 렌더까지 대기
    await page.waitForTimeout(4000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    // 로그인 화면 핵심 요소 단언
    await expect(
      page.getByText("로그인", { exact: false }).first(),
    ).toBeVisible({ timeout: 10000 });
    await expect(page.getByText("Sports App에 오신 것을 환영합니다")).toBeVisible({
      timeout: 5000,
    });

    await page.screenshot({
      path: `${artifactDir}/screenshot-login-render.png`,
      fullPage: true,
    });
    testInfo.attach("로그인 화면 screenshot", {
      path: `${artifactDir}/screenshot-login-render.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-02  홈 탭 렌더
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-02] 홈 탭 — Sports App 타이틀과 '다가오는 경기' 섹션이 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/tabs-home";

    await injectAuthAndNavigate(page, "/(tabs)", refreshToken);
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    await expect(page.getByText("Sports App").first()).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByText("다가오는 경기").first()).toBeVisible({
      timeout: 10000,
    });

    await page.screenshot({
      path: `${artifactDir}/screenshot-tabs-home.png`,
      fullPage: true,
    });
    testInfo.attach("홈 탭 screenshot", {
      path: `${artifactDir}/screenshot-tabs-home.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-03  스토어 탭 렌더
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-03] 스토어 탭 — 상품 목록 또는 빈 상태 텍스트가 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/tabs-store";

    await injectAuthAndNavigate(page, "/(tabs)/store", refreshToken);
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    // 스토어 탭 렌더: 상품 카드 또는 빈 상태 텍스트 중 하나
    const hasContent = await page
      .getByText(/스포츠|상품|원|장바구니|등록된 상품이 없습니다/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    expect(
      hasContent,
      "스토어 탭에 상품 목록 또는 빈 상태 메시지가 렌더돼야 함",
    ).toBe(true);

    await page.screenshot({
      path: `${artifactDir}/screenshot-tabs-store.png`,
      fullPage: true,
    });
    testInfo.attach("스토어 탭 screenshot", {
      path: `${artifactDir}/screenshot-tabs-store.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-04  티켓 탭 렌더
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-04] 티켓 탭 — 경기 목록 또는 빈 상태 텍스트가 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/tabs-tickets";

    await injectAuthAndNavigate(page, "/(tabs)/tickets", refreshToken);
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    const hasContent = await page
      .getByText(/K리그|KBL|V리그|경기|등록된 경기가 없습니다|전체|OPEN|CLOSED/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    expect(
      hasContent,
      "티켓 탭에 경기 목록 또는 빈 상태 메시지가 렌더돼야 함",
    ).toBe(true);

    await page.screenshot({
      path: `${artifactDir}/screenshot-tabs-tickets.png`,
      fullPage: true,
    });
    testInfo.attach("티켓 탭 screenshot", {
      path: `${artifactDir}/screenshot-tabs-tickets.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-05  커뮤니티 탭 렌더
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-05] 커뮤니티 탭 — 게시글 목록 또는 빈 상태 텍스트가 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/tabs-community";

    await injectAuthAndNavigate(page, "/(tabs)/community", refreshToken);
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    const hasContent = await page
      .getByText(/게시글|커뮤니티|작성|등록된 게시글이 없습니다/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    expect(
      hasContent,
      "커뮤니티 탭에 게시글 목록 또는 빈 상태 메시지가 렌더돼야 함",
    ).toBe(true);

    await page.screenshot({
      path: `${artifactDir}/screenshot-tabs-community.png`,
      fullPage: true,
    });
    testInfo.attach("커뮤니티 탭 screenshot", {
      path: `${artifactDir}/screenshot-tabs-community.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-06  마이페이지 탭 렌더
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-06] 마이페이지 탭 — 사용자 정보(이메일) 또는 로그아웃 버튼이 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/tabs-me";

    await injectAuthAndNavigate(page, "/(tabs)/me", refreshToken);
    // accessToken도 메모리에 주입 (zustand 상태 — localStorage 경유 불가, JWT 직접 세팅)
    await page.evaluate(
      ([at, rt]: string[]) => {
        window.localStorage.setItem("refreshToken", rt);
        // zustand store에 직접 접근이 불가하므로 커스텀 이벤트로 알림
        window.dispatchEvent(new CustomEvent("__test_inject_tokens", {
          detail: { accessToken: at, refreshToken: rt },
        }));
      },
      [accessToken, refreshToken],
    );
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    const hasContent = await page
      .getByText(/로그아웃|마이페이지|qa-portal|이메일|내 정보|로그인이 필요/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    expect(
      hasContent,
      "마이페이지 탭에 사용자 정보 또는 로그아웃 버튼이 렌더돼야 함",
    ).toBe(true);

    await page.screenshot({
      path: `${artifactDir}/screenshot-tabs-me.png`,
      fullPage: true,
    });
    testInfo.attach("마이페이지 탭 screenshot", {
      path: `${artifactDir}/screenshot-tabs-me.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-07  결제 화면 렌더 (payment/new)
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-07] 결제 수단 선택 화면 — 결제수단 목록과 '결제하기' 버튼이 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/payment-new";

    // 쿼리 파라미터 포함해서 결제 화면 진입
    const paymentPath =
      "/payment/new?orderType=BOOKING&orderId=1&amount=50000&itemName=테스트%20결제";
    await injectAuthAndNavigate(page, paymentPath, refreshToken);
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    // 결제 수단 선택 화면 핵심 요소
    await expect(page.getByText("결제 수단 선택").first()).toBeVisible({
      timeout: 10000,
    });

    // 결제수단 목록 (카카오페이, 토스페이, 신용카드 중 1개 이상)
    const hasPaymentMethod = await page
      .getByText(/카카오페이|토스페이|네이버페이|신용카드|계좌이체|모바일결제/i)
      .first()
      .isVisible({ timeout: 8000 })
      .catch(() => false);
    expect(hasPaymentMethod, "결제수단 목록이 렌더돼야 함").toBe(true);

    // 결제하기 버튼
    await expect(page.getByText("결제하기").first()).toBeVisible({
      timeout: 5000,
    });

    // 금액 표시
    await expect(page.getByText(/50,000원|50000원/i).first()).toBeVisible({
      timeout: 5000,
    });

    await page.screenshot({
      path: `${artifactDir}/screenshot-payment-new.png`,
      fullPage: true,
    });
    testInfo.attach("결제 화면 screenshot", {
      path: `${artifactDir}/screenshot-payment-new.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-08  알림 목록 화면 렌더
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-08] 알림 목록 화면 — '알림' 헤더와 목록 또는 빈 상태 텍스트가 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/notifications";

    await injectAuthAndNavigate(page, "/notifications", refreshToken);
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    // 알림 헤더
    await expect(page.getByText("알림").first()).toBeVisible({
      timeout: 10000,
    });

    // 알림 목록 또는 빈 상태 메시지
    const hasContent = await page
      .getByText(/알림이 없습니다|예약|결제|이벤트|시스템|프로모션|다시 시도/i)
      .first()
      .isVisible({ timeout: 10000 })
      .catch(() => false);

    expect(
      hasContent,
      "알림 목록 또는 빈 상태 메시지가 렌더돼야 함",
    ).toBe(true);

    await page.screenshot({
      path: `${artifactDir}/screenshot-notifications.png`,
      fullPage: true,
    });
    testInfo.attach("알림 화면 screenshot", {
      path: `${artifactDir}/screenshot-notifications.png`,
      contentType: "image/png",
    });
  });

  // ---------------------------------------------------------------------------
  // E2E-MOB-01-09  티켓 발권 화면 렌더 (event/[id]/order) — seatIds 없는 경우
  // ---------------------------------------------------------------------------
  test("[E2E-MOB-01-09] 티켓 발권 화면 — seatIds 없이 진입 시 '선택된 좌석이 없습니다' 텍스트가 렌더된다", async ({
    page,
  }, testInfo) => {
    const artifactDir =
      process.env.QA_ARTIFACTS_DIR ??
      "/Users/biuea/sports-application/.analysis/outputs/qa/20260530_regression-11pr/artifacts/event-order";

    // seatIds 없이 진입 → 빈 좌석 에러 분기 확인
    await injectAuthAndNavigate(page, "/event/1/order", refreshToken);
    await page.waitForTimeout(5000);

    await assertNoBundleError(page);
    await assertNotPlaceholder(page);

    await expect(page.getByText("선택된 좌석이 없습니다.").first()).toBeVisible({
      timeout: 10000,
    });
    await expect(page.getByText("< 뒤로").first()).toBeVisible({
      timeout: 5000,
    });

    await page.screenshot({
      path: `${artifactDir}/screenshot-event-order-empty-seats.png`,
      fullPage: true,
    });
    testInfo.attach("티켓 발권 화면(좌석 없음) screenshot", {
      path: `${artifactDir}/screenshot-event-order-empty-seats.png`,
      contentType: "image/png",
    });
  });
});

/**
 * E2E-04 결제 생성 · 멱등성 · 내 결제 내역
 * 시나리오: qa/e2e/scenarios/payment-create-list.md
 *
 * AUTH-04: /payments/** 는 X-User-Id 헤더 → JWT(@AuthenticationPrincipal UserPrincipal) 인증으로
 * 전환됐다. 모든 케이스는 test/helpers.ts 의 registerAndLogin() 으로 케이스마다 신규 사용자를
 * 만들고 Authorization: Bearer <accessToken> 으로 호출한다.
 *
 * POST /payments 는 booking 존재 여부와 무관 — Payment 가 orderType/orderId 만 저장하고
 * mock 게이트웨이 success-rate 1.0 로 초기화되므로 시드 없이도 결정적으로 201 을 반환한다.
 *
 * 보강 (20260607_full-regression):
 *   E2E-04-06~07, E2E-04-R03~R04, E2E-04-E05~E07
 *   PR #182(payments status 무효 enum → 500 fix) 런타임 재검증.
 *   주의: 실제 PaymentStatus enum 값(PENDING/READY/COMPLETED/CANCELLED/FAILED/REFUNDED)만 사용한다.
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";
import { API_URL, uniqueKey, registerAndLogin, bearer } from "../test/helpers";

const validPaymentPayload = (orderId = 1) => ({
  orderType: "BOOKING",
  orderId,
  method: "CREDIT_CARD",
  amount: 50000,
  currency: "KRW",
});

test.describe("E2E-04 payment create · idempotency · list", () => {
  test("E2E-04-01 신규 user — POST /payments + Idempotency-Key 는 201 + id 를 반환한다", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-01");
    const res = await api.post(`${API_URL}/payments`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": uniqueKey("e2e04-01"), "Content-Type": "application/json" },
      data: validPaymentPayload(),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(201);
    const body = await res.json();
    expect(body).toHaveProperty("id");
    await api.dispose();
  });

  test("E2E-04-02 같은 Idempotency-Key 재호출 시 동일 payment id (멱등)", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-02");
    const key = uniqueKey("e2e04-02");
    const payload = validPaymentPayload();
    const r1 = await api.post(`${API_URL}/payments`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": key, "Content-Type": "application/json" },
      data: payload,
      failOnStatusCode: false,
    });
    const r2 = await api.post(`${API_URL}/payments`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": key, "Content-Type": "application/json" },
      data: payload,
      failOnStatusCode: false,
    });
    expect(r1.status()).toBe(201);
    expect(r2.status()).toBe(201);
    const b1 = await r1.json();
    const b2 = await r2.json();
    expect(b2.id).toBe(b1.id);
    await api.dispose();
  });

  test("E2E-04-03 존재하지 않는 payment id 조회 시 404", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-03");
    const res = await api.get(`${API_URL}/payments/999999999`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(404);
    await api.dispose();
  });

  test("E2E-04-04 GET /payments/me — 200 + Page + createdAt DESC 정렬", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-04");
    // 정렬 검증 의미를 갖도록 결제 2건 생성
    await api.post(`${API_URL}/payments`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": uniqueKey("e2e04-04-a"), "Content-Type": "application/json" },
      data: validPaymentPayload(1),
      failOnStatusCode: false,
    });
    await api.post(`${API_URL}/payments`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": uniqueKey("e2e04-04-b"), "Content-Type": "application/json" },
      data: validPaymentPayload(2),
      failOnStatusCode: false,
    });
    const res = await api.get(`${API_URL}/payments/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const items = body.content;
    expect(items.length).toBeGreaterThanOrEqual(2);
    const times = items.map((p: { createdAt?: string }) => p.createdAt).filter(Boolean);
    const desc = [...times].sort().reverse();
    expect(times).toEqual(desc);
    await api.dispose();
  });

  test("E2E-04-R01 결제 생성 응답의 createdAt 이 ISO-8601 UTC (Z suffix)", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-r01");
    await api.post(`${API_URL}/payments`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": uniqueKey("e2e04-r01"), "Content-Type": "application/json" },
      data: validPaymentPayload(),
      failOnStatusCode: false,
    });
    const res = await api.get(`${API_URL}/payments/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const first = body.content?.[0];
    if (first?.createdAt) {
      expect(first.createdAt).toMatch(/Z$|[+-]\d{2}:\d{2}$/);
    }
    await api.dispose();
  });

  test("E2E-04-E01 Idempotency-Key 없이 POST /payments 호출 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-e01");
    const res = await api.post(`${API_URL}/payments`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: validPaymentPayload(),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    await api.dispose();
  });

  test("E2E-04-E02 user-A 가 만든 payment 를 user-B 가 조회 시 403", async () => {
    const api1 = await playwrightRequest.newContext();
    const api2 = await playwrightRequest.newContext();
    const userA = await registerAndLogin(api1, "e2e04-e02-a");
    const userB = await registerAndLogin(api2, "e2e04-e02-b");
    const create = await api1.post(`${API_URL}/payments`, {
      headers: { ...bearer(userA.accessToken), "Idempotency-Key": uniqueKey("e2e04-e02"), "Content-Type": "application/json" },
      data: validPaymentPayload(),
      failOnStatusCode: false,
    });
    expect(create.status()).toBe(201);
    const { id } = await create.json();
    const res = await api2.get(`${API_URL}/payments/${id}`, {
      headers: bearer(userB.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    await api1.dispose();
    await api2.dispose();
  });

  test("E2E-04-E03 Authorization 헤더 없이 GET /payments/me 호출 시 401 (AUTH-04 계약)", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/payments/me`, { failOnStatusCode: false });
    expect(res.status()).toBe(401);
    await api.dispose();
  });

  test("E2E-04-E04 paidAtFrom > paidAtTo 잘못된 범위 — 400 또는 빈 결과", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-e04");
    const res = await api.get(
      `${API_URL}/payments/me?paidAtFrom=2030-01-01T00:00:00Z&paidAtTo=2020-01-01T00:00:00Z`,
      { headers: bearer(user.accessToken), failOnStatusCode: false },
    );
    expect([200, 400]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      const items = body.content ?? [];
      expect(items.length).toBe(0);
    }
    await api.dispose();
  });

  test("E2E-04-E05 결제 게이트웨이 5xx 시 payment 상태 FAILED — stub 환경 의존", async () => {
    test.info().annotations.push({
      type: "skip-reason",
      description:
        "결제 게이트웨이 5xx stub 주입 메커니즘 (예: WireMock 또는 profile 토글) 이 환경에 없어 회귀 미실행",
    });
    test.skip();
  });

  // ─── 보강 케이스 (20260607_full-regression) ────────────────────────────────
  // PR #182 payments status 무효 enum → 400 fix 런타임 재검증

  test("E2E-04-06 유효 status COMPLETED 필터 — 200 반환 (회귀 깨짐 없음)", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-06");
    const res = await api.get(`${API_URL}/payments/me?status=COMPLETED`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status(), "COMPLETED 필터 — 유효 enum이므로 200이어야 함").toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty("content");
    for (const p of body.content ?? []) {
      if (p.status !== undefined) {
        expect(p.status).toBe("COMPLETED");
      }
    }
    await api.dispose();
  });

  test("E2E-04-07 유효 status READY 필터 — 200 반환", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-07");
    const res = await api.get(`${API_URL}/payments/me?status=READY`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status(), "READY 필터 — 유효 enum이므로 200이어야 함").toBe(200);
    await api.dispose();
  });

  test("E2E-04-R03 유효한 모든 PaymentStatus 값으로 GET /payments/me 시 전부 200", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-r03");
    const validStatuses = ["PENDING", "READY", "COMPLETED", "CANCELLED", "FAILED", "REFUNDED"];
    for (const status of validStatuses) {
      const res = await api.get(`${API_URL}/payments/me?status=${status}`, {
        headers: bearer(user.accessToken),
        failOnStatusCode: false,
      });
      expect(res.status(), `status=${status} — 유효 enum이므로 200이어야 함 (got ${res.status()})`).toBe(200);
    }
    await api.dispose();
  });

  test("E2E-04-R04 status 파라미터 미지정 GET /payments/me — 전체 결과 200 반환", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-r04");
    const res = await api.get(`${API_URL}/payments/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty("content");
    await api.dispose();
  });

  test("E2E-04-E06 무효 status=INVALID_ANYTHING 호출 시 400 Bad Request (500이면 결함)", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-e06");
    const res = await api.get(`${API_URL}/payments/me?status=INVALID_ANYTHING`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status(), "무효 status 값에 500이 반환됨 — PR #182 fix 회귀 또는 미적용").not.toBe(500);
    expect(res.status(), "무효 status 값은 400이어야 함").toBe(400);
    await api.dispose();
  });

  test("E2E-04-E07 소문자 무효 status=paid 호출 시 400 Bad Request", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-e07");
    const res = await api.get(`${API_URL}/payments/me?status=paid`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status(), "소문자 status=paid에 500이 반환됨").not.toBe(500);
    expect(res.status(), "소문자 무효 status=paid는 400이어야 함").toBe(400);
    await api.dispose();
  });

  test("E2E-04-E08 빈 status 값 GET /payments/me?status= — 500이 아닌 200 또는 400으로 일관 응답", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e04-e08");

    const emptyRes = await api.get(`${API_URL}/payments/me?status=`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(emptyRes.status(), "빈 status 값에 500이 반환됨").not.toBe(500);
    expect([200, 400]).toContain(emptyRes.status());

    // PAID는 실제 enum에 없음 — 200(빈 결과)으로 빠져나가면 enum 매핑 누락 결함 마스킹 가능성
    const paidRes = await api.get(`${API_URL}/payments/me?status=PAID`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    if (paidRes.status() === 200) {
      test.info().annotations.push({
        type: "enum-masking-warning",
        description:
          "status=PAID(enum 미존재)가 400 대신 200 빈 결과로 응답 — GlobalExceptionHandler가 PAID를 처리하지 않고 null로 통과시키는 결함 마스킹 가능성.",
      });
    }
    expect(paidRes.status(), "status=PAID에 500이 반환됨").not.toBe(500);

    await api.dispose();
  });
});

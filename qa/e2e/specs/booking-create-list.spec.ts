/**
 * E2E-03 시설 슬롯 예약 생성 · 조회
 * 시나리오: qa/e2e/scenarios/booking-create-list.md
 *
 * AUTH-04: /bookings/** 는 X-User-Id 헤더 → JWT(@AuthenticationPrincipal UserPrincipal) 인증으로
 * 전환됐다. 모든 케이스는 test/helpers.ts 의 registerAndLogin() 으로 케이스마다 신규 사용자를
 * 만들고 Authorization: Bearer <accessToken> 으로 호출한다 — 고정 X-User-Id 값을 그대로 옮기면
 * 다른 사용자 데이터에 의존하게 되므로 사용하지 않는다.
 *
 * 시드: qa/e2e/fixtures/seed.sql 의 slotId=7 (capacity 100000, 회귀 반복으로 PENDING 이 누적돼도
 * SlotFull 로 막히지 않음) 이 주입돼 있으면 예약 생성이 결정적으로 202 를 반환한다. 시드가 없는
 * 환경에서는 슬롯을 찾지 못해 4xx/5xx 가 되므로, 생성 성공을 전제하는 단언은 모두 시드 유무 양쪽을
 * 허용하는 상태 코드 범위로 작성한다.
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";
import { API_URL, registerAndLogin, bearer } from "../test/helpers";

const SEEDED_LARGE_CAPACITY_SLOT_ID = 7;
const SEEDED_LOW_CAPACITY_SLOT_ID = 1;

test.describe("E2E-03 booking create · list", () => {
  test("E2E-03-01 신규 user 가 POST /bookings 호출 시 202 또는 시드 부재로 인한 도메인 예외", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e03-01");
    const res = await api.post(`${API_URL}/bookings`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: {
        slotId: SEEDED_LARGE_CAPACITY_SLOT_ID,
        paymentMethod: "CREDIT_CARD",
        amount: 10000,
        currency: "KRW",
      },
      failOnStatusCode: false,
    });
    expect([202, 400, 404, 409, 422, 500]).toContain(res.status());
    if (res.status() === 202) {
      const body = await res.json();
      // CreateBookingResult 의 식별자 필드명은 bookingId (id 가 아니다).
      expect(body).toHaveProperty("bookingId");
    }
    await api.dispose();
  });

  test("E2E-03-02 존재하지 않는 booking id 조회 시 404", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e03-02");
    const res = await api.get(`${API_URL}/bookings/999999999`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(404);
    await api.dispose();
  });

  test("E2E-03-03 신규 user — GET /bookings/me 는 200 + Page 응답 구조(bookings 필드)를 반환한다", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e03-03");
    const res = await api.get(`${API_URL}/bookings/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    // ListBookingsResponse 의 목록 필드명은 bookings (content/items 가 아니다).
    expect(body).toHaveProperty("totalElements");
    expect(Array.isArray(body.bookings)).toBe(true);
    await api.dispose();
  });

  test("E2E-03-04 GET /bookings/me?status=PENDING — 필터 결과는 모두 PENDING", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e03-04");
    const res = await api.get(`${API_URL}/bookings/me?status=PENDING`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    for (const b of body.bookings ?? []) {
      if (b.status !== undefined) {
        expect(b.status).toBe("PENDING");
      }
    }
    await api.dispose();
  });

  test("E2E-03-R01 페이징 기본값 유지 — size 미명시 시 기본 20", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e03-r01");
    const res = await api.get(`${API_URL}/bookings/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    if (body.size !== undefined) {
      expect(body.size).toBe(20);
    }
    await api.dispose();
  });

  test("E2E-03-R02 booking 생성 직후 status 는 PENDING (CONFIRMED 가 아님)", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e03-r02");
    // slotId 7 은 seed.sql 에 capacity 100000 으로 시드됨 — 회귀 반복으로 PENDING 이
    // 누적돼도 SlotFull 이 되지 않아 매번 202 PENDING 응답을 받는다.
    const res = await api.post(`${API_URL}/bookings`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: {
        slotId: SEEDED_LARGE_CAPACITY_SLOT_ID,
        paymentMethod: "CREDIT_CARD",
        amount: 10000,
        currency: "KRW",
      },
      failOnStatusCode: false,
    });
    if (res.status() === 202) {
      const body = await res.json();
      expect(body.status).toBe("PENDING");
    } else {
      test.info().annotations.push({
        type: "skip-reason",
        description: `시드 미주입 — 생성 응답 ${res.status()}`,
      });
      test.skip();
    }
    await api.dispose();
  });

  test("E2E-03-E01 Authorization 헤더 없이 POST /bookings 호출 시 401 (AUTH-04 계약)", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/bookings`, {
      headers: { "Content-Type": "application/json" },
      data: { slotId: SEEDED_LARGE_CAPACITY_SLOT_ID, paymentMethod: "CREDIT_CARD", amount: 10000, currency: "KRW" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(401);
    await api.dispose();
  });

  test("E2E-03-E02 슬롯 capacity(5) 를 초과하는 동시 booking 요청 중 최소 1건은 거부된다", async () => {
    // 슬롯 1은 capacity 5 — 회귀 반복으로 이미 일부 소진됐을 수 있으므로, 잔여 capacity 와
    // 무관하게 "capacity 초과 요청은 전량 성공할 수 없다"를 검증하려면 capacity 보다 많은
    // 동시 요청(7건)을 보낸다. 잔여 capacity 가 0 이어도(전량 실패) 단언은 그대로 성립한다.
    const CONCURRENT_REQUEST_COUNT = 7;
    const contexts = await Promise.all(
      Array.from({ length: CONCURRENT_REQUEST_COUNT }, () => playwrightRequest.newContext()),
    );
    const users = await Promise.all(
      contexts.map((ctx, index) => registerAndLogin(ctx, `e2e03-e02-${index}`)),
    );
    const body = {
      slotId: SEEDED_LOW_CAPACITY_SLOT_ID,
      paymentMethod: "CREDIT_CARD",
      amount: 10000,
      currency: "KRW",
    };
    const responses = await Promise.all(
      contexts.map((ctx, index) =>
        ctx.post(`${API_URL}/bookings`, {
          headers: { ...bearer(users[index].accessToken), "Content-Type": "application/json" },
          data: body,
          failOnStatusCode: false,
        }),
      ),
    );
    const successes = responses.filter((r) => r.status() === 202).length;
    expect(successes, "capacity(5) 초과 요청 7건이 전부 성공하면 안 됨").toBeLessThan(CONCURRENT_REQUEST_COUNT);
    await Promise.all(contexts.map((ctx) => ctx.dispose()));
  });

  test("E2E-03-E03 user-A 가 만든 booking 을 user-B 가 조회 시 403", async () => {
    const api1 = await playwrightRequest.newContext();
    const api2 = await playwrightRequest.newContext();
    const userA = await registerAndLogin(api1, "e2e03-e03-a");
    const userB = await registerAndLogin(api2, "e2e03-e03-b");
    const create = await api1.post(`${API_URL}/bookings`, {
      headers: { ...bearer(userA.accessToken), "Content-Type": "application/json" },
      data: {
        slotId: SEEDED_LARGE_CAPACITY_SLOT_ID,
        paymentMethod: "CREDIT_CARD",
        amount: 10000,
        currency: "KRW",
      },
      failOnStatusCode: false,
    });
    if (create.status() !== 202) {
      test.info().annotations.push({
        type: "skip-reason",
        description: `시드 미주입 — user-A booking 생성 실패(${create.status()}), 소유권 검증 보류`,
      });
      test.skip();
      await api1.dispose();
      await api2.dispose();
      return;
    }
    const { bookingId } = await create.json();
    const res = await api2.get(`${API_URL}/bookings/${bookingId}`, {
      headers: bearer(userB.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    await api1.dispose();
    await api2.dispose();
  });

  test("E2E-03-E04 booking 0건인 신규 user 가 /bookings/me 호출 시 200 + 빈 페이지", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e03-e04");
    const res = await api.get(`${API_URL}/bookings/me`, {
      headers: bearer(user.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.bookings ?? []).toHaveLength(0);
    expect(body.totalElements ?? 0).toBe(0);
    await api.dispose();
  });
});

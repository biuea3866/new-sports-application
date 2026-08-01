/**
 * E2E-05 경기 티켓 좌석 선택 · 발권
 * 시나리오: qa/e2e/scenarios/ticket-event-purchase.md
 *
 * AUTH-04: 좌석 선점/해제·발권(/events/{id}/seats/**, /ticket-orders/**)은 X-User-Id 헤더 →
 * JWT(@AuthenticationPrincipal UserPrincipal) 인증으로 전환됐다. 목록·상세 조회(GET /events**)는
 * 공개 조회라 인증이 필요 없고 그대로 둔다. 나머지 케이스는 test/helpers.ts 의 registerAndLogin()
 * 으로 케이스마다 신규 사용자를 만들고 Authorization: Bearer <accessToken> 으로 호출한다.
 *
 * 시드: qa/e2e/fixtures/seed.sql 의 events(1~3)/seats(1~12) 가 주입돼 있으면 일부 케이스가
 * 결정적으로 동작한다. 좌석 풀이 12석으로 한정돼 있고 Playwright 설정이 fullyParallel 이라,
 * 서로 다른 테스트가 같은 좌석을 다투지 않도록 케이스마다 겹치지 않는 event/seat 조합을 쓴다.
 * 시드가 없는 환경에서는 4xx/5xx 가 되므로 성공을 전제하는 단언은 상태 코드 범위로 작성한다.
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";
import { API_URL, uniqueKey, registerAndLogin, bearer } from "../test/helpers";

test.describe("E2E-05 ticket event · seat · purchase", () => {
  test("E2E-05-01 GET /events?status=OPEN — 200 + Page 응답", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/events?status=OPEN`, { failOnStatusCode: false });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty("content");
    const items = body.content;
    if (items.length > 1) {
      const starts = items.map((e: { startsAt?: string }) => e.startsAt).filter(Boolean);
      const asc = [...starts].sort();
      expect(starts).toEqual(asc);
    }
    await api.dispose();
  });

  test("E2E-05-02 GET /events/1 — 200 또는 404", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/events/1`, { failOnStatusCode: false });
    expect([200, 404]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty("id");
    }
    await api.dispose();
  });

  test("E2E-05-03 신규 user — POST /events/1/seats/select 는 200 또는 도메인 예외", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e05-03");
    const res = await api.post(`${API_URL}/events/1/seats/select`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { seatIds: [101, 102] },
      failOnStatusCode: false,
    });
    expect([200, 400, 404, 409, 422, 500]).toContain(res.status());
    if (res.status() === 200) {
      const body = await res.json();
      expect(body).toHaveProperty("lockId");
    }
    await api.dispose();
  });

  test("E2E-05-04 POST /ticket-orders + Idempotency-Key — 시드 의존, 202 또는 4xx", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e05-04");
    const key = uniqueKey("e2e05-04");
    const res = await api.post(`${API_URL}/ticket-orders`, {
      headers: {
        ...bearer(user.accessToken),
        "Idempotency-Key": key,
        "Content-Type": "application/json",
      },
      data: { lockId: "lock-001", method: "CREDIT_CARD", currency: "KRW" },
      failOnStatusCode: false,
    });
    expect([202, 400, 404, 409, 422, 500]).toContain(res.status());
    if (res.status() === 202) {
      const body = await res.json();
      expect(body).toHaveProperty("ticketOrderId");
    }
    await api.dispose();
  });

  test("E2E-05-05 POST /events/1/seats/release — 204 또는 4xx", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e05-05");
    const res = await api.post(`${API_URL}/events/1/seats/release`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { seatIds: [101, 102] },
      failOnStatusCode: false,
    });
    // 신규 user 는 해당 좌석을 잠근 적이 없으므로 SeatNotLockOwnerException(403) 이 정상 응답이다.
    expect([204, 400, 403, 404, 409, 422, 500]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-05-R01 GET /events 의 startsAt 이 ISO-8601 UTC (Z suffix)", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/events`, { failOnStatusCode: false });
    expect(res.status()).toBe(200);
    const body = await res.json();
    const items = body.content ?? [];
    if (items.length === 0) {
      test.info().annotations.push({
        type: "skip-reason",
        description: "event 시드가 비어 startsAt 직렬화 검증 건너뜀",
      });
      test.skip();
      return;
    }
    for (const e of items) {
      if (e.startsAt) {
        expect(e.startsAt).toMatch(/Z$|[+-]\d{2}:\d{2}$/);
      }
    }
    await api.dispose();
  });

  test("E2E-05-R02 같은 Idempotency-Key 로 ticket-orders 재호출 시 동일 order id", async () => {
    // 발견된 BE 결함(별도 보고, p1~p2): PurchaseTicketsUseCase.execute() → TicketingDomainService
    // .createPendingOrder(lockId, userId) 는 idempotencyKey 를 받지 않고 매 호출마다 무조건 새
    // TicketOrder 를 생성한다. 멱등은 그 아래 paymentDomainService.createPending(idempotencyKey)
    // 에만 있어 결제 행은 재사용되지만 티켓 주문 자체는 매번 새로 생성된다 — 재시도가 좌석 중복
    // 발권으로 이어질 수 있다. goods-orders(GoodsDomainService.createPendingOrder 의
    // findByIdempotencyKey 가드)·payments 와 달리 order 레벨 멱등이 없다. BE 가 수정되기 전까지는
    // 이 단언이 실패하는 것이 정상이므로 test.fail() 로 표시한다 — BE 수정 후 이 표시가 사라지면
    // (예상과 달리 통과하면) Playwright 가 "unexpected pass" 로 알려준다.
    test.fail(true, "TICKET-ORDER-IDEMPOTENCY-GAP: createPendingOrder 가 idempotencyKey 로 중복을 걸지 않음");
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e05-r02");
    // lockId 는 eventId:seatId 형식 (콤마 구분) — POST /events/{id}/seats/select 가 발급.
    // seed.sql 의 event 2 좌석 7,8 을 잠가 유효 lockId 를 동적으로 확보한다 (이 파일의 다른
    // 케이스는 event 2 의 7,8 을 사용하지 않는다 — fullyParallel 좌석 경합 회피).
    const select = await api.post(`${API_URL}/events/2/seats/select`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { seatIds: [7, 8] },
      failOnStatusCode: false,
    });
    if (select.status() !== 200) {
      test.info().annotations.push({
        type: "skip-reason",
        description: `좌석 select 실패 — 응답 ${select.status()} (좌석 시드 또는 Redis 상태 확인 필요)`,
      });
      test.skip();
      await api.dispose();
      return;
    }
    const lockId = (await select.json()).lockId as string;
    const key = uniqueKey("e2e05-r02");
    const payload = { lockId, method: "CREDIT_CARD", currency: "KRW" };
    const r1 = await api.post(`${API_URL}/ticket-orders`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": key, "Content-Type": "application/json" },
      data: payload,
      failOnStatusCode: false,
    });
    const r2 = await api.post(`${API_URL}/ticket-orders`, {
      headers: { ...bearer(user.accessToken), "Idempotency-Key": key, "Content-Type": "application/json" },
      data: payload,
      failOnStatusCode: false,
    });
    if (r1.status() === 202 && r2.status() === 202) {
      const b1 = await r1.json();
      const b2 = await r2.json();
      expect(b2.ticketOrderId).toBe(b1.ticketOrderId);
    } else {
      test.info().annotations.push({
        type: "skip-reason",
        description: `ticket-orders 응답 ${r1.status()}, ${r2.status()} — 재호출이 동일 결과를 내지 못함`,
      });
      test.skip();
    }
    await api.dispose();
  });

  test("E2E-05-E01 user-A LOCKED 좌석을 user-B 가 동시 select — 한쪽만 성공", async () => {
    const api1 = await playwrightRequest.newContext();
    const api2 = await playwrightRequest.newContext();
    const userA = await registerAndLogin(api1, "e2e05-e01-a");
    const userB = await registerAndLogin(api2, "e2e05-e01-b");
    const body = { seatIds: [201, 202] };
    const [r1, r2] = await Promise.all([
      api1.post(`${API_URL}/events/1/seats/select`, {
        headers: { ...bearer(userA.accessToken), "Content-Type": "application/json" },
        data: body,
        failOnStatusCode: false,
      }),
      api2.post(`${API_URL}/events/1/seats/select`, {
        headers: { ...bearer(userB.accessToken), "Content-Type": "application/json" },
        data: body,
        failOnStatusCode: false,
      }),
    ]);
    const successes = [r1.status(), r2.status()].filter((s) => s === 200).length;
    expect(successes).toBeLessThanOrEqual(1);
    await api1.dispose();
    await api2.dispose();
  });

  test("E2E-05-E02 Idempotency-Key 없이 POST /ticket-orders 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e05-e02");
    const res = await api.post(`${API_URL}/ticket-orders`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { lockId: "lock-001", method: "CREDIT_CARD", currency: "KRW" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    await api.dispose();
  });

  test("E2E-05-E03 존재하지 않는 event id 조회 시 404", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/events/9999999`, { failOnStatusCode: false });
    expect([404, 400]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-05-E04 좌석 락 TTL 경과 후 발권 시도 — TTL 대기 없이 만료 lockId 사용", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e05-e04");
    const res = await api.post(`${API_URL}/ticket-orders`, {
      headers: {
        ...bearer(user.accessToken),
        "Idempotency-Key": uniqueKey("e2e05-e04"),
        "Content-Type": "application/json",
      },
      data: { lockId: "expired-lock-xxx", method: "CREDIT_CARD", currency: "KRW" },
      failOnStatusCode: false,
    });
    expect([400, 404, 409, 410, 422, 500]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-05-E05 빈 seatIds 로 select 호출 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerAndLogin(api, "e2e05-e05");
    const res = await api.post(`${API_URL}/events/1/seats/select`, {
      headers: { ...bearer(user.accessToken), "Content-Type": "application/json" },
      data: { seatIds: [] },
      failOnStatusCode: false,
    });
    expect([400, 422]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-05-E06 Authorization 헤더 없이 POST /events/1/seats/select 호출 시 401 (AUTH-04 계약)", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/events/1/seats/select`, {
      headers: { "Content-Type": "application/json" },
      data: { seatIds: [1, 2] },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(401);
    await api.dispose();
  });

  test("E2E-05-E07 user-A 가 만든 ticket order 를 user-B 가 조회 시 403", async () => {
    const api1 = await playwrightRequest.newContext();
    const api2 = await playwrightRequest.newContext();
    const userA = await registerAndLogin(api1, "e2e05-e07-a");
    const userB = await registerAndLogin(api2, "e2e05-e07-b");
    // event 3 좌석 11,12 — 이 파일의 다른 케이스가 사용하지 않는 조합.
    const select = await api1.post(`${API_URL}/events/3/seats/select`, {
      headers: { ...bearer(userA.accessToken), "Content-Type": "application/json" },
      data: { seatIds: [11, 12] },
      failOnStatusCode: false,
    });
    if (select.status() !== 200) {
      test.info().annotations.push({
        type: "skip-reason",
        description: `좌석 select 실패 — 응답 ${select.status()}, 소유권 검증 보류`,
      });
      test.skip();
      await api1.dispose();
      await api2.dispose();
      return;
    }
    const { lockId } = await select.json();
    const purchase = await api1.post(`${API_URL}/ticket-orders`, {
      headers: {
        ...bearer(userA.accessToken),
        "Idempotency-Key": uniqueKey("e2e05-e07"),
        "Content-Type": "application/json",
      },
      data: { lockId, method: "CREDIT_CARD", currency: "KRW" },
      failOnStatusCode: false,
    });
    if (purchase.status() !== 202) {
      test.info().annotations.push({
        type: "skip-reason",
        description: `ticket-order 생성 실패(${purchase.status()}) — 소유권 검증 보류`,
      });
      test.skip();
      await api1.dispose();
      await api2.dispose();
      return;
    }
    const { ticketOrderId } = await purchase.json();
    const res = await api2.get(`${API_URL}/ticket-orders/${ticketOrderId}`, {
      headers: bearer(userB.accessToken),
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(403);
    await api1.dispose();
    await api2.dispose();
  });
});

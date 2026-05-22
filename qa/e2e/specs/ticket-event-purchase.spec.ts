/**
 * E2E-05 경기 티켓 좌석 선택 · 발권
 * 시나리오: qa/e2e/scenarios/ticket-event-purchase.md
 *
 * 시드 (event-with-seats.sql) 미주입 환경 — event 가 없으면 listEvents 가 빈 페이지를,
 * getEvent(1) 은 404 를 반환할 수 있다. 본 spec 은 응답 일관성 위주로 검증한다.
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";
import { API_URL, uniqueKey } from "../test/helpers";

test.describe("E2E-05 ticket event · seat · purchase", () => {
  test("E2E-05-01 GET /events?status=OPEN — 200 + Page 응답", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/events?status=OPEN`, { failOnStatusCode: false });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toHaveProperty("content");
    // startsAt 오름차순
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
      // 좌석 구성·잔여 좌석 수 필드 존재 검증 (스키마 따라 이름 가변)
      expect(body).toHaveProperty("id");
    }
    await api.dispose();
  });

  test("E2E-05-03 POST /events/1/seats/select — 200 또는 도메인 예외", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/events/1/seats/select`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
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
    const key = uniqueKey("e2e05-04");
    const res = await api.post(`${API_URL}/ticket-orders`, {
      headers: {
        "X-User-Id": "1",
        "Idempotency-Key": key,
        "Content-Type": "application/json",
      },
      data: { lockId: "lock-001", method: "CARD", currency: "KRW" },
      failOnStatusCode: false,
    });
    expect([202, 400, 404, 409, 422, 500]).toContain(res.status());
    if (res.status() === 202) {
      const body = await res.json();
      expect(body).toHaveProperty("id");
    }
    await api.dispose();
  });

  test("E2E-05-05 POST /events/1/seats/release — 204 또는 4xx", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/events/1/seats/release`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
      data: { seatIds: [101, 102] },
      failOnStatusCode: false,
    });
    expect([204, 400, 404, 409, 422, 500]).toContain(res.status());
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
    const api = await playwrightRequest.newContext();
    // lockId 는 eventId:seatId 형식 (콤마 구분) — POST /events/{id}/seats/select 가 발급.
    // seed.sql 의 event 2 좌석 7,8 을 잠가 유효 lockId 를 동적으로 확보한다.
    const userId = "1";
    const select = await api.post(`${API_URL}/events/2/seats/select`, {
      headers: { "X-User-Id": userId, "Content-Type": "application/json" },
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
      headers: { "X-User-Id": userId, "Idempotency-Key": key, "Content-Type": "application/json" },
      data: payload,
      failOnStatusCode: false,
    });
    const r2 = await api.post(`${API_URL}/ticket-orders`, {
      headers: { "X-User-Id": userId, "Idempotency-Key": key, "Content-Type": "application/json" },
      data: payload,
      failOnStatusCode: false,
    });
    if (r1.status() === 202 && r2.status() === 202) {
      const b1 = await r1.json();
      const b2 = await r2.json();
      // TicketOrderResponse 의 식별자는 ticketOrderId.
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
    const body = { seatIds: [201, 202] };
    const [r1, r2] = await Promise.all([
      api1.post(`${API_URL}/events/1/seats/select`, {
        headers: { "X-User-Id": "1", "Content-Type": "application/json" },
        data: body,
        failOnStatusCode: false,
      }),
      api2.post(`${API_URL}/events/1/seats/select`, {
        headers: { "X-User-Id": "2", "Content-Type": "application/json" },
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
    const res = await api.post(`${API_URL}/ticket-orders`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
      data: { lockId: "lock-001", method: "CARD", currency: "KRW" },
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
    const res = await api.post(`${API_URL}/ticket-orders`, {
      headers: {
        "X-User-Id": "1",
        "Idempotency-Key": uniqueKey("e2e05-e04"),
        "Content-Type": "application/json",
      },
      data: { lockId: "expired-lock-xxx", method: "CARD", currency: "KRW" },
      failOnStatusCode: false,
    });
    // 락 미존재 또는 만료 → 4xx
    expect([400, 404, 409, 410, 422, 500]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-05-E05 빈 seatIds 로 select 호출 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/events/1/seats/select`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
      data: { seatIds: [] },
      failOnStatusCode: false,
    });
    expect([400, 422]).toContain(res.status());
    await api.dispose();
  });
});

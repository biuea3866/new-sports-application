# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ticket-event-purchase.spec.ts >> E2E-05 ticket event · seat · purchase >> E2E-05-05 POST /events/1/seats/release — 204 또는 4xx
- Location: specs/ticket-event-purchase.spec.ts:75:7

# Error details

```
Error: expect(received).toContain(expected) // indexOf

Expected value: 429
Received array: [204, 400, 404, 409, 422, 500]
```

# Test source

```ts
  1   | /**
  2   |  * E2E-05 경기 티켓 좌석 선택 · 발권
  3   |  * 시나리오: qa/e2e/scenarios/ticket-event-purchase.md
  4   |  *
  5   |  * 시드 (event-with-seats.sql) 미주입 환경 — event 가 없으면 listEvents 가 빈 페이지를,
  6   |  * getEvent(1) 은 404 를 반환할 수 있다. 본 spec 은 응답 일관성 위주로 검증한다.
  7   |  */
  8   | import { test, expect, request as playwrightRequest } from "@playwright/test";
  9   | import { API_URL, uniqueKey } from "../test/helpers";
  10  | 
  11  | test.describe("E2E-05 ticket event · seat · purchase", () => {
  12  |   test("E2E-05-01 GET /events?status=OPEN — 200 + Page 응답", async () => {
  13  |     const api = await playwrightRequest.newContext();
  14  |     const res = await api.get(`${API_URL}/events?status=OPEN`, { failOnStatusCode: false });
  15  |     expect(res.status()).toBe(200);
  16  |     const body = await res.json();
  17  |     expect(body).toHaveProperty("content");
  18  |     // startsAt 오름차순
  19  |     const items = body.content;
  20  |     if (items.length > 1) {
  21  |       const starts = items.map((e: { startsAt?: string }) => e.startsAt).filter(Boolean);
  22  |       const asc = [...starts].sort();
  23  |       expect(starts).toEqual(asc);
  24  |     }
  25  |     await api.dispose();
  26  |   });
  27  | 
  28  |   test("E2E-05-02 GET /events/1 — 200 또는 404", async () => {
  29  |     const api = await playwrightRequest.newContext();
  30  |     const res = await api.get(`${API_URL}/events/1`, { failOnStatusCode: false });
  31  |     expect([200, 404]).toContain(res.status());
  32  |     if (res.status() === 200) {
  33  |       const body = await res.json();
  34  |       // 좌석 구성·잔여 좌석 수 필드 존재 검증 (스키마 따라 이름 가변)
  35  |       expect(body).toHaveProperty("id");
  36  |     }
  37  |     await api.dispose();
  38  |   });
  39  | 
  40  |   test("E2E-05-03 POST /events/1/seats/select — 200 또는 도메인 예외", async () => {
  41  |     const api = await playwrightRequest.newContext();
  42  |     const res = await api.post(`${API_URL}/events/1/seats/select`, {
  43  |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  44  |       data: { seatIds: [101, 102] },
  45  |       failOnStatusCode: false,
  46  |     });
  47  |     expect([200, 400, 404, 409, 422, 500]).toContain(res.status());
  48  |     if (res.status() === 200) {
  49  |       const body = await res.json();
  50  |       expect(body).toHaveProperty("lockId");
  51  |     }
  52  |     await api.dispose();
  53  |   });
  54  | 
  55  |   test("E2E-05-04 POST /ticket-orders + Idempotency-Key — 시드 의존, 202 또는 4xx", async () => {
  56  |     const api = await playwrightRequest.newContext();
  57  |     const key = uniqueKey("e2e05-04");
  58  |     const res = await api.post(`${API_URL}/ticket-orders`, {
  59  |       headers: {
  60  |         "X-User-Id": "1",
  61  |         "Idempotency-Key": key,
  62  |         "Content-Type": "application/json",
  63  |       },
  64  |       data: { lockId: "lock-001", method: "CARD", currency: "KRW" },
  65  |       failOnStatusCode: false,
  66  |     });
  67  |     expect([202, 400, 404, 409, 422, 500]).toContain(res.status());
  68  |     if (res.status() === 202) {
  69  |       const body = await res.json();
  70  |       expect(body).toHaveProperty("id");
  71  |     }
  72  |     await api.dispose();
  73  |   });
  74  | 
  75  |   test("E2E-05-05 POST /events/1/seats/release — 204 또는 4xx", async () => {
  76  |     const api = await playwrightRequest.newContext();
  77  |     const res = await api.post(`${API_URL}/events/1/seats/release`, {
  78  |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  79  |       data: { seatIds: [101, 102] },
  80  |       failOnStatusCode: false,
  81  |     });
> 82  |     expect([204, 400, 404, 409, 422, 500]).toContain(res.status());
      |                                            ^ Error: expect(received).toContain(expected) // indexOf
  83  |     await api.dispose();
  84  |   });
  85  | 
  86  |   test("E2E-05-R01 GET /events 의 startsAt 이 ISO-8601 UTC (Z suffix)", async () => {
  87  |     const api = await playwrightRequest.newContext();
  88  |     const res = await api.get(`${API_URL}/events`, { failOnStatusCode: false });
  89  |     expect(res.status()).toBe(200);
  90  |     const body = await res.json();
  91  |     const items = body.content ?? [];
  92  |     if (items.length === 0) {
  93  |       test.info().annotations.push({
  94  |         type: "skip-reason",
  95  |         description: "event 시드가 비어 startsAt 직렬화 검증 건너뜀",
  96  |       });
  97  |       test.skip();
  98  |       return;
  99  |     }
  100 |     for (const e of items) {
  101 |       if (e.startsAt) {
  102 |         expect(e.startsAt).toMatch(/Z$|[+-]\d{2}:\d{2}$/);
  103 |       }
  104 |     }
  105 |     await api.dispose();
  106 |   });
  107 | 
  108 |   test("E2E-05-R02 같은 Idempotency-Key 로 ticket-orders 재호출 시 동일 order id", async () => {
  109 |     const api = await playwrightRequest.newContext();
  110 |     // lockId 는 eventId:seatId 형식 (콤마 구분) — POST /events/{id}/seats/select 가 발급.
  111 |     // seed.sql 의 event 2 좌석 7,8 을 잠가 유효 lockId 를 동적으로 확보한다.
  112 |     const userId = "1";
  113 |     const select = await api.post(`${API_URL}/events/2/seats/select`, {
  114 |       headers: { "X-User-Id": userId, "Content-Type": "application/json" },
  115 |       data: { seatIds: [7, 8] },
  116 |       failOnStatusCode: false,
  117 |     });
  118 |     if (select.status() !== 200) {
  119 |       test.info().annotations.push({
  120 |         type: "skip-reason",
  121 |         description: `좌석 select 실패 — 응답 ${select.status()} (좌석 시드 또는 Redis 상태 확인 필요)`,
  122 |       });
  123 |       test.skip();
  124 |       await api.dispose();
  125 |       return;
  126 |     }
  127 |     const lockId = (await select.json()).lockId as string;
  128 |     const key = uniqueKey("e2e05-r02");
  129 |     const payload = { lockId, method: "CREDIT_CARD", currency: "KRW" };
  130 |     const r1 = await api.post(`${API_URL}/ticket-orders`, {
  131 |       headers: { "X-User-Id": userId, "Idempotency-Key": key, "Content-Type": "application/json" },
  132 |       data: payload,
  133 |       failOnStatusCode: false,
  134 |     });
  135 |     const r2 = await api.post(`${API_URL}/ticket-orders`, {
  136 |       headers: { "X-User-Id": userId, "Idempotency-Key": key, "Content-Type": "application/json" },
  137 |       data: payload,
  138 |       failOnStatusCode: false,
  139 |     });
  140 |     if (r1.status() === 202 && r2.status() === 202) {
  141 |       const b1 = await r1.json();
  142 |       const b2 = await r2.json();
  143 |       // TicketOrderResponse 의 식별자는 ticketOrderId.
  144 |       expect(b2.ticketOrderId).toBe(b1.ticketOrderId);
  145 |     } else {
  146 |       test.info().annotations.push({
  147 |         type: "skip-reason",
  148 |         description: `ticket-orders 응답 ${r1.status()}, ${r2.status()} — 재호출이 동일 결과를 내지 못함`,
  149 |       });
  150 |       test.skip();
  151 |     }
  152 |     await api.dispose();
  153 |   });
  154 | 
  155 |   test("E2E-05-E01 user-A LOCKED 좌석을 user-B 가 동시 select — 한쪽만 성공", async () => {
  156 |     const api1 = await playwrightRequest.newContext();
  157 |     const api2 = await playwrightRequest.newContext();
  158 |     const body = { seatIds: [201, 202] };
  159 |     const [r1, r2] = await Promise.all([
  160 |       api1.post(`${API_URL}/events/1/seats/select`, {
  161 |         headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  162 |         data: body,
  163 |         failOnStatusCode: false,
  164 |       }),
  165 |       api2.post(`${API_URL}/events/1/seats/select`, {
  166 |         headers: { "X-User-Id": "2", "Content-Type": "application/json" },
  167 |         data: body,
  168 |         failOnStatusCode: false,
  169 |       }),
  170 |     ]);
  171 |     const successes = [r1.status(), r2.status()].filter((s) => s === 200).length;
  172 |     expect(successes).toBeLessThanOrEqual(1);
  173 |     await api1.dispose();
  174 |     await api2.dispose();
  175 |   });
  176 | 
  177 |   test("E2E-05-E02 Idempotency-Key 없이 POST /ticket-orders 시 400", async () => {
  178 |     const api = await playwrightRequest.newContext();
  179 |     const res = await api.post(`${API_URL}/ticket-orders`, {
  180 |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  181 |       data: { lockId: "lock-001", method: "CARD", currency: "KRW" },
  182 |       failOnStatusCode: false,
```
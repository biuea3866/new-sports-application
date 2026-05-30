# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: ticket-event-purchase.spec.ts >> E2E-05 ticket event · seat · purchase >> E2E-05-E05 빈 seatIds 로 select 호출 시 400
- Location: specs/ticket-event-purchase.spec.ts:211:7

# Error details

```
Error: expect(received).toContain(expected) // indexOf

Expected value: 429
Received array: [400, 422]
```

# Test source

```ts
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
  183 |     });
  184 |     expect(res.status()).toBe(400);
  185 |     await api.dispose();
  186 |   });
  187 | 
  188 |   test("E2E-05-E03 존재하지 않는 event id 조회 시 404", async () => {
  189 |     const api = await playwrightRequest.newContext();
  190 |     const res = await api.get(`${API_URL}/events/9999999`, { failOnStatusCode: false });
  191 |     expect([404, 400]).toContain(res.status());
  192 |     await api.dispose();
  193 |   });
  194 | 
  195 |   test("E2E-05-E04 좌석 락 TTL 경과 후 발권 시도 — TTL 대기 없이 만료 lockId 사용", async () => {
  196 |     const api = await playwrightRequest.newContext();
  197 |     const res = await api.post(`${API_URL}/ticket-orders`, {
  198 |       headers: {
  199 |         "X-User-Id": "1",
  200 |         "Idempotency-Key": uniqueKey("e2e05-e04"),
  201 |         "Content-Type": "application/json",
  202 |       },
  203 |       data: { lockId: "expired-lock-xxx", method: "CARD", currency: "KRW" },
  204 |       failOnStatusCode: false,
  205 |     });
  206 |     // 락 미존재 또는 만료 → 4xx
  207 |     expect([400, 404, 409, 410, 422, 500]).toContain(res.status());
  208 |     await api.dispose();
  209 |   });
  210 | 
  211 |   test("E2E-05-E05 빈 seatIds 로 select 호출 시 400", async () => {
  212 |     const api = await playwrightRequest.newContext();
  213 |     const res = await api.post(`${API_URL}/events/1/seats/select`, {
  214 |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  215 |       data: { seatIds: [] },
  216 |       failOnStatusCode: false,
  217 |     });
> 218 |     expect([400, 422]).toContain(res.status());
      |                        ^ Error: expect(received).toContain(expected) // indexOf
  219 |     await api.dispose();
  220 |   });
  221 | });
  222 | 
```
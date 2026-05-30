# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: booking-create-list.spec.ts >> E2E-03 booking create · list >> E2E-03-E04 booking 0건 user 가 /bookings/me 호출 시 200 + 빈 페이지
- Location: specs/booking-create-list.spec.ts:157:7

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 200
Received: 429
```

# Test source

```ts
  63  |       if (b.status !== undefined) {
  64  |         expect(b.status).toBe("PENDING");
  65  |       }
  66  |     }
  67  |     await api.dispose();
  68  |   });
  69  | 
  70  |   test("E2E-03-R01 페이징 기본값 유지 — size 미명시 시 기본 20", async () => {
  71  |     const api = await playwrightRequest.newContext();
  72  |     const res = await api.get(`${API_URL}/bookings/me`, {
  73  |       headers: { "X-User-Id": "1" },
  74  |       failOnStatusCode: false,
  75  |     });
  76  |     expect(res.status()).toBe(200);
  77  |     const body = await res.json();
  78  |     const size = body.pageable?.pageSize ?? body.size;
  79  |     if (size !== undefined) {
  80  |       expect(size).toBe(20);
  81  |     }
  82  |     await api.dispose();
  83  |   });
  84  | 
  85  |   test("E2E-03-R02 booking 생성 직후 status 는 PENDING (CONFIRMED 가 아님)", async () => {
  86  |     const api = await playwrightRequest.newContext();
  87  |     // BE PaymentMethod enum 은 CREDIT_CARD/BANK_TRANSFER/VIRTUAL_ACCOUNT/MOBILE_PAY.
  88  |     // slotId 7 은 seed.sql 에 capacity 100000 으로 시드됨 — 회귀 반복으로 PENDING 이
  89  |     // 누적돼도 SlotFull 이 되지 않아 매번 202 PENDING 응답을 받는다.
  90  |     const res = await api.post(`${API_URL}/bookings`, {
  91  |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  92  |       data: { slotId: 7, paymentMethod: "CREDIT_CARD", amount: 10000, currency: "KRW" },
  93  |       failOnStatusCode: false,
  94  |     });
  95  |     if (res.status() === 202) {
  96  |       const body = await res.json();
  97  |       const status = body.status ?? body.bookingStatus;
  98  |       if (status !== undefined) {
  99  |         expect(status).toBe("PENDING");
  100 |       }
  101 |     } else {
  102 |       test.info().annotations.push({
  103 |         type: "skip-reason",
  104 |         description: `시드 미주입 — 생성 응답 ${res.status()}`,
  105 |       });
  106 |       test.skip();
  107 |     }
  108 |     await api.dispose();
  109 |   });
  110 | 
  111 |   test("E2E-03-E01 X-User-Id 헤더 없이 POST /bookings 시 4xx", async () => {
  112 |     const api = await playwrightRequest.newContext();
  113 |     const res = await api.post(`${API_URL}/bookings`, {
  114 |       headers: { "Content-Type": "application/json" },
  115 |       data: { slotId: 1, paymentMethod: "CARD", amount: 10000, currency: "KRW" },
  116 |       failOnStatusCode: false,
  117 |     });
  118 |     expect([400, 401, 403, 500]).toContain(res.status());
  119 |     await api.dispose();
  120 |   });
  121 | 
  122 |   test("E2E-03-E02 동일 슬롯 동시 booking 시 한 쪽만 성공", async () => {
  123 |     const api1 = await playwrightRequest.newContext();
  124 |     const api2 = await playwrightRequest.newContext();
  125 |     const body = { slotId: 1, paymentMethod: "CARD", amount: 10000, currency: "KRW" };
  126 |     const [r1, r2] = await Promise.all([
  127 |       api1.post(`${API_URL}/bookings`, {
  128 |         headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  129 |         data: body,
  130 |         failOnStatusCode: false,
  131 |       }),
  132 |       api2.post(`${API_URL}/bookings`, {
  133 |         headers: { "X-User-Id": "2", "Content-Type": "application/json" },
  134 |         data: body,
  135 |         failOnStatusCode: false,
  136 |       }),
  137 |     ]);
  138 |     const statuses = [r1.status(), r2.status()];
  139 |     // 두 응답이 모두 동일 결과 (양쪽 모두 4xx — 시드 부재) 이거나 한쪽만 202
  140 |     const successes = statuses.filter((s) => s === 202).length;
  141 |     expect(successes).toBeLessThanOrEqual(1);
  142 |     await api1.dispose();
  143 |     await api2.dispose();
  144 |   });
  145 | 
  146 |   test("E2E-03-E03 user-A 의 booking 을 user-B 가 조회 시 403/404", async () => {
  147 |     const api = await playwrightRequest.newContext();
  148 |     // 임의의 id — 다른 user 의 자원
  149 |     const res = await api.get(`${API_URL}/bookings/1`, {
  150 |       headers: { "X-User-Id": "999999" },
  151 |       failOnStatusCode: false,
  152 |     });
  153 |     expect([403, 404]).toContain(res.status());
  154 |     await api.dispose();
  155 |   });
  156 | 
  157 |   test("E2E-03-E04 booking 0건 user 가 /bookings/me 호출 시 200 + 빈 페이지", async () => {
  158 |     const api = await playwrightRequest.newContext();
  159 |     const res = await api.get(`${API_URL}/bookings/me`, {
  160 |       headers: { "X-User-Id": "9999999" },
  161 |       failOnStatusCode: false,
  162 |     });
> 163 |     expect(res.status()).toBe(200);
      |                          ^ Error: expect(received).toBe(expected) // Object.is equality
  164 |     const body = await res.json();
  165 |     const items = body.content ?? body.items ?? [];
  166 |     expect(items.length).toBe(0);
  167 |     expect(body.totalElements ?? 0).toBe(0);
  168 |     await api.dispose();
  169 |   });
  170 | });
  171 | 
```
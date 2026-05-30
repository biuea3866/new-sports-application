# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: payment-create-list.spec.ts >> E2E-04 payment create · idempotency · list >> E2E-04-03 GET /payments/{id} — 200 또는 4xx
- Location: specs/payment-create-list.spec.ts:72:7

# Error details

```
Error: expect(received).toContain(expected) // indexOf

Expected value: 401
Received array: [200, 403, 404]
```

# Test source

```ts
  1   | /**
  2   |  * E2E-04 결제 생성 · 멱등성 · 내 결제 내역
  3   |  * 시나리오: qa/e2e/scenarios/payment-create-list.md
  4   |  *
  5   |  * 주의: 시드 (booking-pending.sql) 미주입 — 결제 생성은 booking 의존성이 있어
  6   |  * 응답 상태 코드/스키마/멱등 헤더 동작 위주로 검증.
  7   |  */
  8   | import { test, expect, request as playwrightRequest } from "@playwright/test";
  9   | import { API_URL, uniqueKey } from "../test/helpers";
  10  | 
  11  | test.describe("E2E-04 payment create · idempotency · list", () => {
  12  |   test("E2E-04-01 POST /payments + Idempotency-Key — 201 또는 도메인 예외", async () => {
  13  |     const api = await playwrightRequest.newContext();
  14  |     const key = uniqueKey("e2e04-01");
  15  |     const res = await api.post(`${API_URL}/payments`, {
  16  |       headers: { "Idempotency-Key": key, "Content-Type": "application/json" },
  17  |       data: {
  18  |         orderType: "BOOKING",
  19  |         orderId: 1,
  20  |         method: "CARD",
  21  |         amount: 50000,
  22  |         currency: "KRW",
  23  |       },
  24  |       failOnStatusCode: false,
  25  |     });
  26  |     // 시드가 있으면 201, 없으면 도메인 예외 (4xx)
  27  |     expect([201, 400, 404, 409, 422, 500]).toContain(res.status());
  28  |     if (res.status() === 201) {
  29  |       const body = await res.json();
  30  |       expect(body).toHaveProperty("id");
  31  |     }
  32  |     await api.dispose();
  33  |   });
  34  | 
  35  |   test("E2E-04-02 같은 Idempotency-Key 재호출 시 동일 payment id (멱등)", async () => {
  36  |     const api = await playwrightRequest.newContext();
  37  |     const key = uniqueKey("e2e04-02");
  38  |     // BE PaymentMethod enum 은 CREDIT_CARD/BANK_TRANSFER/VIRTUAL_ACCOUNT/MOBILE_PAY.
  39  |     // POST /payments 는 booking 존재 여부와 무관 — Payment 가 orderType/orderId 만 저장하고
  40  |     // mock 게이트웨이 success-rate 1.0 -> 201. 멱등은 idempotency_key unique 제약 기반.
  41  |     const payload = {
  42  |       orderType: "BOOKING",
  43  |       orderId: 1,
  44  |       method: "CREDIT_CARD",
  45  |       amount: 50000,
  46  |       currency: "KRW",
  47  |     };
  48  |     const r1 = await api.post(`${API_URL}/payments`, {
  49  |       headers: { "Idempotency-Key": key, "Content-Type": "application/json" },
  50  |       data: payload,
  51  |       failOnStatusCode: false,
  52  |     });
  53  |     const r2 = await api.post(`${API_URL}/payments`, {
  54  |       headers: { "Idempotency-Key": key, "Content-Type": "application/json" },
  55  |       data: payload,
  56  |       failOnStatusCode: false,
  57  |     });
  58  |     if (r1.status() === 201 && r2.status() === 201) {
  59  |       const b1 = await r1.json();
  60  |       const b2 = await r2.json();
  61  |       expect(b2.id).toBe(b1.id);
  62  |     } else {
  63  |       test.info().annotations.push({
  64  |         type: "skip-reason",
  65  |         description: `시드 미주입 — 1차/2차 응답: ${r1.status()}, ${r2.status()}`,
  66  |       });
  67  |       test.skip();
  68  |     }
  69  |     await api.dispose();
  70  |   });
  71  | 
  72  |   test("E2E-04-03 GET /payments/{id} — 200 또는 4xx", async () => {
  73  |     const api = await playwrightRequest.newContext();
  74  |     const res = await api.get(`${API_URL}/payments/1`, {
  75  |       headers: { "X-User-Id": "1" },
  76  |       failOnStatusCode: false,
  77  |     });
> 78  |     expect([200, 403, 404]).toContain(res.status());
      |                             ^ Error: expect(received).toContain(expected) // indexOf
  79  |     await api.dispose();
  80  |   });
  81  | 
  82  |   test("E2E-04-04 GET /payments/me — 200 + Page + createdAt DESC 정렬", async () => {
  83  |     const api = await playwrightRequest.newContext();
  84  |     const res = await api.get(`${API_URL}/payments/me`, {
  85  |       headers: { "X-User-Id": "1" },
  86  |       failOnStatusCode: false,
  87  |     });
  88  |     expect(res.status()).toBe(200);
  89  |     const body = await res.json();
  90  |     expect(body).toHaveProperty("content");
  91  |     const items = body.content;
  92  |     if (items.length > 1) {
  93  |       const times = items.map((p: { createdAt?: string }) => p.createdAt).filter(Boolean);
  94  |       const desc = [...times].sort().reverse();
  95  |       expect(times).toEqual(desc);
  96  |     }
  97  |     await api.dispose();
  98  |   });
  99  | 
  100 |   test("E2E-04-05 GET /payments/me?status=PAID — 결과는 모두 PAID", async () => {
  101 |     const api = await playwrightRequest.newContext();
  102 |     const res = await api.get(`${API_URL}/payments/me?status=PAID`, {
  103 |       headers: { "X-User-Id": "1" },
  104 |       failOnStatusCode: false,
  105 |     });
  106 |     expect(res.status()).toBe(200);
  107 |     const body = await res.json();
  108 |     for (const p of body.content ?? []) {
  109 |       if (p.status !== undefined) {
  110 |         expect(p.status).toBe("PAID");
  111 |       }
  112 |     }
  113 |     await api.dispose();
  114 |   });
  115 | 
  116 |   test("E2E-04-R01 결제 생성 응답의 createdAt 이 ISO-8601 UTC (Z suffix)", async () => {
  117 |     const api = await playwrightRequest.newContext();
  118 |     const res = await api.get(`${API_URL}/payments/me`, {
  119 |       headers: { "X-User-Id": "1" },
  120 |       failOnStatusCode: false,
  121 |     });
  122 |     expect(res.status()).toBe(200);
  123 |     const body = await res.json();
  124 |     const items = body.content ?? [];
  125 |     if (items.length === 0) {
  126 |       test.info().annotations.push({
  127 |         type: "skip-reason",
  128 |         description: "결제 시드가 비어 ISO-8601 검증 건너뜀",
  129 |       });
  130 |       test.skip();
  131 |       return;
  132 |     }
  133 |     const first = items[0];
  134 |     if (first.createdAt) {
  135 |       expect(first.createdAt).toMatch(/Z$|[+-]\d{2}:\d{2}$/);
  136 |     }
  137 |     await api.dispose();
  138 |   });
  139 | 
  140 |   test("E2E-04-R02 GET /payments/me 기본 정렬 createdAt DESC", async () => {
  141 |     // E2E-04-04 와 동일 검증 — 다른 user 로도 일관 확인
  142 |     const api = await playwrightRequest.newContext();
  143 |     const res = await api.get(`${API_URL}/payments/me`, {
  144 |       headers: { "X-User-Id": "2" },
  145 |       failOnStatusCode: false,
  146 |     });
  147 |     expect(res.status()).toBe(200);
  148 |     const body = await res.json();
  149 |     const items = body.content ?? [];
  150 |     if (items.length > 1) {
  151 |       const times = items.map((p: { createdAt?: string }) => p.createdAt).filter(Boolean);
  152 |       const desc = [...times].sort().reverse();
  153 |       expect(times).toEqual(desc);
  154 |     }
  155 |     await api.dispose();
  156 |   });
  157 | 
  158 |   test("E2E-04-E01 Idempotency-Key 없이 POST /payments 호출 시 400", async () => {
  159 |     const api = await playwrightRequest.newContext();
  160 |     const res = await api.post(`${API_URL}/payments`, {
  161 |       headers: { "Content-Type": "application/json" },
  162 |       data: {
  163 |         orderType: "BOOKING",
  164 |         orderId: 1,
  165 |         method: "CARD",
  166 |         amount: 50000,
  167 |         currency: "KRW",
  168 |       },
  169 |       failOnStatusCode: false,
  170 |     });
  171 |     expect(res.status()).toBe(400);
  172 |     await api.dispose();
  173 |   });
  174 | 
  175 |   test("E2E-04-E02 다른 user 의 payment id 조회 시 403/404", async () => {
  176 |     const api = await playwrightRequest.newContext();
  177 |     const res = await api.get(`${API_URL}/payments/1`, {
  178 |       headers: { "X-User-Id": "999999" },
```
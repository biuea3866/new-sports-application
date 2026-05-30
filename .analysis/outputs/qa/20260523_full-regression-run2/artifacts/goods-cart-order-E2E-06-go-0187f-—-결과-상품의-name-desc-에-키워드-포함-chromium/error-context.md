# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: goods-cart-order.spec.ts >> E2E-06 goods search · cart · order >> E2E-06-02 keyword=유니폼 — 결과 상품의 name/desc 에 키워드 포함
- Location: specs/goods-cart-order.spec.ts:27:7

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 200
Received: 429
```

# Test source

```ts
  1   | /**
  2   |  * E2E-06 굿즈 검색 · 장바구니 · 주문
  3   |  * 시나리오: qa/e2e/scenarios/goods-cart-order.md
  4   |  *
  5   |  * 시드 (products-multi-category.sql) 미주입 — 검색 응답은 빈 페이지일 수 있다.
  6   |  * 카트/주문 케이스는 상품 시드가 없으면 도메인 예외가 정상 응답이다.
  7   |  */
  8   | import { test, expect, request as playwrightRequest } from "@playwright/test";
  9   | import { API_URL, uniqueKey } from "../test/helpers";
  10  | 
  11  | test.describe("E2E-06 goods search · cart · order", () => {
  12  |   test("E2E-06-01 GET /products?category=APPAREL — 200 + 모두 APPAREL", async () => {
  13  |     const api = await playwrightRequest.newContext();
  14  |     const res = await api.get(`${API_URL}/products?category=APPAREL`, {
  15  |       failOnStatusCode: false,
  16  |     });
  17  |     expect(res.status()).toBe(200);
  18  |     const body = await res.json();
  19  |     for (const p of body.content ?? []) {
  20  |       if (p.category !== undefined) {
  21  |         expect(p.category).toBe("APPAREL");
  22  |       }
  23  |     }
  24  |     await api.dispose();
  25  |   });
  26  | 
  27  |   test("E2E-06-02 keyword=유니폼 — 결과 상품의 name/desc 에 키워드 포함", async () => {
  28  |     const api = await playwrightRequest.newContext();
  29  |     const res = await api.get(`${API_URL}/products?keyword=${encodeURIComponent("유니폼")}`, {
  30  |       failOnStatusCode: false,
  31  |     });
> 32  |     expect(res.status()).toBe(200);
      |                          ^ Error: expect(received).toBe(expected) // Object.is equality
  33  |     const body = await res.json();
  34  |     for (const p of body.content ?? []) {
  35  |       const text = `${p.name ?? ""} ${p.description ?? ""}`;
  36  |       if (text.trim()) {
  37  |         expect(text).toContain("유니폼");
  38  |       }
  39  |     }
  40  |     await api.dispose();
  41  |   });
  42  | 
  43  |   test("E2E-06-03 priceMin=20000&priceMax=50000 — 결과 가격이 범위 내", async () => {
  44  |     const api = await playwrightRequest.newContext();
  45  |     const res = await api.get(`${API_URL}/products?priceMin=20000&priceMax=50000`, {
  46  |       failOnStatusCode: false,
  47  |     });
  48  |     expect(res.status()).toBe(200);
  49  |     const body = await res.json();
  50  |     for (const p of body.content ?? []) {
  51  |       const price = Number(p.price);
  52  |       if (!Number.isNaN(price)) {
  53  |         expect(price).toBeGreaterThanOrEqual(20000);
  54  |         expect(price).toBeLessThanOrEqual(50000);
  55  |       }
  56  |     }
  57  |     await api.dispose();
  58  |   });
  59  | 
  60  |   test("E2E-06-04 GET /products/popular?category=EQUIPMENT — 200 + 배열", async () => {
  61  |     const api = await playwrightRequest.newContext();
  62  |     const res = await api.get(`${API_URL}/products/popular?category=EQUIPMENT`, {
  63  |       failOnStatusCode: false,
  64  |     });
  65  |     expect(res.status()).toBe(200);
  66  |     const body = await res.json();
  67  |     expect(Array.isArray(body)).toBe(true);
  68  |     await api.dispose();
  69  |   });
  70  | 
  71  |   test("E2E-06-05 POST /cart/items — 200 또는 4xx(상품 부재)", async () => {
  72  |     const api = await playwrightRequest.newContext();
  73  |     const res = await api.post(`${API_URL}/cart/items`, {
  74  |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  75  |       data: { productId: 1, quantity: 2 },
  76  |       failOnStatusCode: false,
  77  |     });
  78  |     expect([200, 400, 404, 422, 500]).toContain(res.status());
  79  |     await api.dispose();
  80  |   });
  81  | 
  82  |   test("E2E-06-06 GET /cart/me — 200 + cart 응답", async () => {
  83  |     const api = await playwrightRequest.newContext();
  84  |     const res = await api.get(`${API_URL}/cart/me`, {
  85  |       headers: { "X-User-Id": "1" },
  86  |       failOnStatusCode: false,
  87  |     });
  88  |     expect(res.status()).toBe(200);
  89  |     const body = await res.json();
  90  |     expect(body).toBeTruthy();
  91  |     await api.dispose();
  92  |   });
  93  | 
  94  |   test("E2E-06-07 POST /goods-orders + Idempotency-Key — 202 또는 도메인 예외", async () => {
  95  |     const api = await playwrightRequest.newContext();
  96  |     const key = uniqueKey("e2e06-07");
  97  |     const res = await api.post(`${API_URL}/goods-orders`, {
  98  |       headers: {
  99  |         "X-User-Id": "1",
  100 |         "Idempotency-Key": key,
  101 |         "Content-Type": "application/json",
  102 |       },
  103 |       data: { method: "CARD", fromCart: true, items: [] },
  104 |       failOnStatusCode: false,
  105 |     });
  106 |     expect([202, 400, 404, 409, 422, 500]).toContain(res.status());
  107 |     if (res.status() === 202) {
  108 |       const body = await res.json();
  109 |       expect(body).toHaveProperty("id");
  110 |     }
  111 |     await api.dispose();
  112 |   });
  113 | 
  114 |   test("E2E-06-R01 GET /products 기본 정렬 — sort 미명시 시 동일 응답", async () => {
  115 |     const api = await playwrightRequest.newContext();
  116 |     const r1 = await api.get(`${API_URL}/products`, { failOnStatusCode: false });
  117 |     const r2 = await api.get(`${API_URL}/products?sort=recent`, { failOnStatusCode: false });
  118 |     expect(r1.status()).toBe(200);
  119 |     expect(r2.status()).toBe(200);
  120 |     const b1 = await r1.json();
  121 |     const b2 = await r2.json();
  122 |     // 두 응답의 첫 아이템 id 가 같아야 함
  123 |     const id1 = b1.content?.[0]?.id;
  124 |     const id2 = b2.content?.[0]?.id;
  125 |     if (id1 !== undefined && id2 !== undefined) {
  126 |       expect(id1).toBe(id2);
  127 |     }
  128 |     await api.dispose();
  129 |   });
  130 | 
  131 |   test("E2E-06-R02 같은 Idempotency-Key 로 /goods-orders 재호출 시 동일 order id", async () => {
  132 |     const api = await playwrightRequest.newContext();
```
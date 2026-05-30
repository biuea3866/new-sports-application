# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: goods-cart-order.spec.ts >> E2E-06 goods search · cart · order >> E2E-06-R02 같은 Idempotency-Key 로 /goods-orders 재호출 시 동일 order id
- Location: specs/goods-cart-order.spec.ts:131:7

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 19
Received: 20
```

# Test source

```ts
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
  133 |     const key = uniqueKey("e2e06-r02");
  134 |     // BE CreateGoodsOrderRequest 는 fromCart=false 일 때 items 필수.
  135 |     // items 가 비면 EmptyOrderException -> 4xx. seed.sql 의 product 5 로 유효 주문 구성.
  136 |     // method 는 PaymentMethod enum (CREDIT_CARD).
  137 |     const payload = {
  138 |       method: "CREDIT_CARD",
  139 |       fromCart: false,
  140 |       items: [{ productId: 5, quantity: 1 }],
  141 |     };
  142 |     const r1 = await api.post(`${API_URL}/goods-orders`, {
  143 |       headers: {
  144 |         "X-User-Id": "1",
  145 |         "Idempotency-Key": key,
  146 |         "Content-Type": "application/json",
  147 |       },
  148 |       data: payload,
  149 |       failOnStatusCode: false,
  150 |     });
  151 |     const r2 = await api.post(`${API_URL}/goods-orders`, {
  152 |       headers: {
  153 |         "X-User-Id": "1",
  154 |         "Idempotency-Key": key,
  155 |         "Content-Type": "application/json",
  156 |       },
  157 |       data: payload,
  158 |       failOnStatusCode: false,
  159 |     });
  160 |     if (r1.status() === 202 && r2.status() === 202) {
  161 |       const b1 = await r1.json();
  162 |       const b2 = await r2.json();
> 163 |       expect(b2.id).toBe(b1.id);
      |                     ^ Error: expect(received).toBe(expected) // Object.is equality
  164 |     } else {
  165 |       test.info().annotations.push({
  166 |         type: "skip-reason",
  167 |         description: `시드 미주입 — 응답 ${r1.status()}, ${r2.status()}`,
  168 |       });
  169 |       test.skip();
  170 |     }
  171 |     await api.dispose();
  172 |   });
  173 | 
  174 |   test("E2E-06-R03 DELETE /cart/items/{id} 후 GET /cart/me 시 해당 item 사라짐", async () => {
  175 |     const api = await playwrightRequest.newContext();
  176 |     // 임의의 itemId
  177 |     const del = await api.delete(`${API_URL}/cart/items/999999`, {
  178 |       headers: { "X-User-Id": "1" },
  179 |       failOnStatusCode: false,
  180 |     });
  181 |     expect([200, 204, 403, 404]).toContain(del.status());
  182 |     const cart = await api.get(`${API_URL}/cart/me`, {
  183 |       headers: { "X-User-Id": "1" },
  184 |       failOnStatusCode: false,
  185 |     });
  186 |     expect(cart.status()).toBe(200);
  187 |     const body = await cart.json();
  188 |     const items = body.items ?? [];
  189 |     expect(items.some((i: { id?: number }) => i.id === 999999)).toBe(false);
  190 |     await api.dispose();
  191 |   });
  192 | 
  193 |   test("E2E-06-E01 품절 상품 추가 — 시드 의존, 응답 일관성만 검증", async () => {
  194 |     const api = await playwrightRequest.newContext();
  195 |     // 999999 같은 비존재 productId 로 추가 시도 — 4xx 가 정상
  196 |     const res = await api.post(`${API_URL}/cart/items`, {
  197 |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  198 |       data: { productId: 999999, quantity: 1 },
  199 |       failOnStatusCode: false,
  200 |     });
  201 |     expect([400, 404, 409, 422, 500]).toContain(res.status());
  202 |     await api.dispose();
  203 |   });
  204 | 
  205 |   test("E2E-06-E02 quantity=0 또는 음수 추가 시 400", async () => {
  206 |     const api = await playwrightRequest.newContext();
  207 |     const res = await api.post(`${API_URL}/cart/items`, {
  208 |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  209 |       data: { productId: 1, quantity: 0 },
  210 |       failOnStatusCode: false,
  211 |     });
  212 |     expect([400, 422]).toContain(res.status());
  213 | 
  214 |     const res2 = await api.post(`${API_URL}/cart/items`, {
  215 |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  216 |       data: { productId: 1, quantity: -1 },
  217 |       failOnStatusCode: false,
  218 |     });
  219 |     expect([400, 422]).toContain(res2.status());
  220 |     await api.dispose();
  221 |   });
  222 | 
  223 |   test("E2E-06-E03 다른 user 의 cart item PATCH 시 403/404", async () => {
  224 |     const api = await playwrightRequest.newContext();
  225 |     const res = await api.patch(`${API_URL}/cart/items/1`, {
  226 |       headers: { "X-User-Id": "999999", "Content-Type": "application/json" },
  227 |       data: { quantity: 5 },
  228 |       failOnStatusCode: false,
  229 |     });
  230 |     expect([403, 404, 400]).toContain(res.status());
  231 |     await api.dispose();
  232 |   });
  233 | 
  234 |   test("E2E-06-E04 빈 cart 에서 /goods-orders 호출 시 도메인 예외", async () => {
  235 |     const api = await playwrightRequest.newContext();
  236 |     // 먼저 cart 비우기
  237 |     await api.delete(`${API_URL}/cart`, {
  238 |       headers: { "X-User-Id": "9999999" },
  239 |       failOnStatusCode: false,
  240 |     });
  241 |     const res = await api.post(`${API_URL}/goods-orders`, {
  242 |       headers: {
  243 |         "X-User-Id": "9999999",
  244 |         "Idempotency-Key": uniqueKey("e2e06-e04"),
  245 |         "Content-Type": "application/json",
  246 |       },
  247 |       data: { method: "CARD", fromCart: true, items: [] },
  248 |       failOnStatusCode: false,
  249 |     });
  250 |     expect([400, 404, 409, 422, 500]).toContain(res.status());
  251 |     await api.dispose();
  252 |   });
  253 | 
  254 |   test("E2E-06-E05 DELETE /cart 후 GET /cart/me 시 빈 cart", async () => {
  255 |     const api = await playwrightRequest.newContext();
  256 |     const del = await api.delete(`${API_URL}/cart`, {
  257 |       headers: { "X-User-Id": "1" },
  258 |       failOnStatusCode: false,
  259 |     });
  260 |     expect([200, 204]).toContain(del.status());
  261 |     const cart = await api.get(`${API_URL}/cart/me`, {
  262 |       headers: { "X-User-Id": "1" },
  263 |       failOnStatusCode: false,
```
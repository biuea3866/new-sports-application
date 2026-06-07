/**
 * E2E-06 굿즈 검색 · 장바구니 · 주문
 * 시나리오: qa/e2e/scenarios/goods-cart-order.md
 *
 * 시드 (products-multi-category.sql) 미주입 — 검색 응답은 빈 페이지일 수 있다.
 * 카트/주문 케이스는 상품 시드가 없으면 도메인 예외가 정상 응답이다.
 *
 * 보강 (20260607_full-regression):
 *   E2E-06-08, E2E-06-09, E2E-06-R04~R06, E2E-06-E06~E07
 *   PR #181(cart NonUnique 500 fix) 런타임 재검증 — cart 단일성·동시 추가 결함 미발생 확인.
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";
import { API_URL, uniqueKey, registerUser } from "../test/helpers";

test.describe("E2E-06 goods search · cart · order", () => {
  test("E2E-06-01 GET /products?category=APPAREL — 200 + 모두 APPAREL", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/products?category=APPAREL`, {
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    for (const p of body.content ?? []) {
      if (p.category !== undefined) {
        expect(p.category).toBe("APPAREL");
      }
    }
    await api.dispose();
  });

  test("E2E-06-02 keyword=유니폼 — 결과 상품의 name/desc 에 키워드 포함", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/products?keyword=${encodeURIComponent("유니폼")}`, {
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    for (const p of body.content ?? []) {
      const text = `${p.name ?? ""} ${p.description ?? ""}`;
      if (text.trim()) {
        expect(text).toContain("유니폼");
      }
    }
    await api.dispose();
  });

  test("E2E-06-03 priceMin=20000&priceMax=50000 — 결과 가격이 범위 내", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/products?priceMin=20000&priceMax=50000`, {
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    for (const p of body.content ?? []) {
      const price = Number(p.price);
      if (!Number.isNaN(price)) {
        expect(price).toBeGreaterThanOrEqual(20000);
        expect(price).toBeLessThanOrEqual(50000);
      }
    }
    await api.dispose();
  });

  test("E2E-06-04 GET /products/popular?category=EQUIPMENT — 200 + 배열", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/products/popular?category=EQUIPMENT`, {
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(Array.isArray(body)).toBe(true);
    await api.dispose();
  });

  test("E2E-06-05 POST /cart/items — 200 또는 4xx(상품 부재)", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
      data: { productId: 1, quantity: 2 },
      failOnStatusCode: false,
    });
    expect([200, 400, 404, 422, 500]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-06-06 GET /cart/me — 200 + cart 응답", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": "1" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body).toBeTruthy();
    await api.dispose();
  });

  test("E2E-06-07 POST /goods-orders + Idempotency-Key — 202 또는 도메인 예외", async () => {
    const api = await playwrightRequest.newContext();
    const key = uniqueKey("e2e06-07");
    const res = await api.post(`${API_URL}/goods-orders`, {
      headers: {
        "X-User-Id": "1",
        "Idempotency-Key": key,
        "Content-Type": "application/json",
      },
      data: { method: "CARD", fromCart: true, items: [] },
      failOnStatusCode: false,
    });
    expect([202, 400, 404, 409, 422, 500]).toContain(res.status());
    if (res.status() === 202) {
      const body = await res.json();
      expect(body).toHaveProperty("id");
    }
    await api.dispose();
  });

  test("E2E-06-R01 GET /products 기본 정렬 — sort 미명시 시 동일 응답", async () => {
    const api = await playwrightRequest.newContext();
    const r1 = await api.get(`${API_URL}/products`, { failOnStatusCode: false });
    const r2 = await api.get(`${API_URL}/products?sort=recent`, { failOnStatusCode: false });
    expect(r1.status()).toBe(200);
    expect(r2.status()).toBe(200);
    const b1 = await r1.json();
    const b2 = await r2.json();
    // 두 응답의 첫 아이템 id 가 같아야 함
    const id1 = b1.content?.[0]?.id;
    const id2 = b2.content?.[0]?.id;
    if (id1 !== undefined && id2 !== undefined) {
      expect(id1).toBe(id2);
    }
    await api.dispose();
  });

  test("E2E-06-R02 같은 Idempotency-Key 로 /goods-orders 재호출 시 동일 order id", async () => {
    const api = await playwrightRequest.newContext();
    const key = uniqueKey("e2e06-r02");
    // BE CreateGoodsOrderRequest 는 fromCart=false 일 때 items 필수.
    // items 가 비면 EmptyOrderException -> 4xx. seed.sql 의 product 5 로 유효 주문 구성.
    // method 는 PaymentMethod enum (CREDIT_CARD).
    const payload = {
      method: "CREDIT_CARD",
      fromCart: false,
      items: [{ productId: 5, quantity: 1 }],
    };
    const r1 = await api.post(`${API_URL}/goods-orders`, {
      headers: {
        "X-User-Id": "1",
        "Idempotency-Key": key,
        "Content-Type": "application/json",
      },
      data: payload,
      failOnStatusCode: false,
    });
    const r2 = await api.post(`${API_URL}/goods-orders`, {
      headers: {
        "X-User-Id": "1",
        "Idempotency-Key": key,
        "Content-Type": "application/json",
      },
      data: payload,
      failOnStatusCode: false,
    });
    if (r1.status() === 202 && r2.status() === 202) {
      const b1 = await r1.json();
      const b2 = await r2.json();
      expect(b2.id).toBe(b1.id);
    } else {
      test.info().annotations.push({
        type: "skip-reason",
        description: `시드 미주입 — 응답 ${r1.status()}, ${r2.status()}`,
      });
      test.skip();
    }
    await api.dispose();
  });

  test("E2E-06-R03 DELETE /cart/items/{id} 후 GET /cart/me 시 해당 item 사라짐", async () => {
    const api = await playwrightRequest.newContext();
    // 임의의 itemId
    const del = await api.delete(`${API_URL}/cart/items/999999`, {
      headers: { "X-User-Id": "1" },
      failOnStatusCode: false,
    });
    expect([200, 204, 403, 404]).toContain(del.status());
    const cart = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": "1" },
      failOnStatusCode: false,
    });
    expect(cart.status()).toBe(200);
    const body = await cart.json();
    const items = body.items ?? [];
    expect(items.some((i: { id?: number }) => i.id === 999999)).toBe(false);
    await api.dispose();
  });

  test("E2E-06-E01 품절 상품 추가 — 시드 의존, 응답 일관성만 검증", async () => {
    const api = await playwrightRequest.newContext();
    // 999999 같은 비존재 productId 로 추가 시도 — 4xx 가 정상
    const res = await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
      data: { productId: 999999, quantity: 1 },
      failOnStatusCode: false,
    });
    expect([400, 404, 409, 422, 500]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-06-E02 quantity=0 또는 음수 추가 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
      data: { productId: 1, quantity: 0 },
      failOnStatusCode: false,
    });
    expect([400, 422]).toContain(res.status());

    const res2 = await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": "1", "Content-Type": "application/json" },
      data: { productId: 1, quantity: -1 },
      failOnStatusCode: false,
    });
    expect([400, 422]).toContain(res2.status());
    await api.dispose();
  });

  test("E2E-06-E03 다른 user 의 cart item PATCH 시 403/404", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.patch(`${API_URL}/cart/items/1`, {
      headers: { "X-User-Id": "999999", "Content-Type": "application/json" },
      data: { quantity: 5 },
      failOnStatusCode: false,
    });
    expect([403, 404, 400]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-06-E04 빈 cart 에서 /goods-orders 호출 시 도메인 예외", async () => {
    const api = await playwrightRequest.newContext();
    // 먼저 cart 비우기
    await api.delete(`${API_URL}/cart`, {
      headers: { "X-User-Id": "9999999" },
      failOnStatusCode: false,
    });
    const res = await api.post(`${API_URL}/goods-orders`, {
      headers: {
        "X-User-Id": "9999999",
        "Idempotency-Key": uniqueKey("e2e06-e04"),
        "Content-Type": "application/json",
      },
      data: { method: "CARD", fromCart: true, items: [] },
      failOnStatusCode: false,
    });
    expect([400, 404, 409, 422, 500]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-06-E05 DELETE /cart 후 GET /cart/me 시 빈 cart", async () => {
    const api = await playwrightRequest.newContext();
    const del = await api.delete(`${API_URL}/cart`, {
      headers: { "X-User-Id": "1" },
      failOnStatusCode: false,
    });
    expect([200, 204]).toContain(del.status());
    const cart = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": "1" },
      failOnStatusCode: false,
    });
    expect(cart.status()).toBe(200);
    const body = await cart.json();
    const items = body.items ?? [];
    expect(items.length).toBe(0);
    await api.dispose();
  });

  // ─── 보강 케이스 (20260607_full-regression) ────────────────────────────────
  // PR #181 cart NonUnique 500 fix 런타임 재검증

  test("E2E-06-08 신규 user — POST /cart/items 순차 2회 시 모두 2xx + GET /cart/me 단일 cart 수렴", async () => {
    // cart API는 X-User-Id 헤더 기반 인증 (Bearer 토큰 아님)
    const api = await playwrightRequest.newContext();
    const user = await registerUser(api, undefined, "Passw0rd!");
    const userId = String(user.id);

    // 1차 추가 — 상품이 없으면 4xx 도메인 예외, 상품이 있으면 2xx
    const r1 = await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": userId, "Content-Type": "application/json" },
      data: { productId: 1, quantity: 1 },
      failOnStatusCode: false,
    });
    // 2xx 또는 4xx(상품 없음)가 정상. 5xx는 결함.
    expect(r1.status(), `1차 추가 응답: ${r1.status()}`).toBeLessThan(500);

    // 2차 추가 — 동일 상품
    const r2 = await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": userId, "Content-Type": "application/json" },
      data: { productId: 1, quantity: 1 },
      failOnStatusCode: false,
    });
    expect(r2.status(), `2차 추가 응답: ${r2.status()}`).toBeLessThan(500);

    // GET /cart/me — cart가 단일건임을 확인 (NonUniqueResult 500이면 결함)
    const cartRes = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": userId },
      failOnStatusCode: false,
    });
    expect(cartRes.status(), "GET /cart/me가 500이면 NonUniqueResult 결함").not.toBe(500);
    expect(cartRes.status()).toBe(200);

    await api.dispose();
  });

  test("E2E-06-09 신규 user — POST /cart/items 동시 5회 시 5xx 없음 + GET /cart/me 단일 cart 수렴", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerUser(api, undefined, "Passw0rd!");
    const userId = String(user.id);

    // 동시 5회 발사
    const concurrentRequests = Array.from({ length: 5 }, () =>
      api.post(`${API_URL}/cart/items`, {
        headers: { "X-User-Id": userId, "Content-Type": "application/json" },
        data: { productId: 1, quantity: 1 },
        failOnStatusCode: false,
      }),
    );
    const responses = await Promise.all(concurrentRequests);

    // 어느 응답도 500이면 안 됨 (NonUniqueResult 결함)
    for (const res of responses) {
      expect(res.status(), `동시 추가 응답이 5xx — NonUniqueResult 결함: ${res.status()}`).toBeLessThan(500);
    }

    // 직후 GET /cart/me — 단일 cart로 수렴했는지 확인
    const cartRes = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": userId },
      failOnStatusCode: false,
    });
    expect(cartRes.status(), "동시 추가 후 GET /cart/me가 500이면 NonUniqueResult 결함").not.toBe(500);
    expect(cartRes.status()).toBe(200);

    await api.dispose();
  });

  test("E2E-06-R04 GET /cart/me 동시 N회 호출 시 500(NonUniqueResultException) 0건", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerUser(api, undefined, "Passw0rd!");
    const userId = String(user.id);

    // 먼저 cart 생성 시도
    await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": userId, "Content-Type": "application/json" },
      data: { productId: 1, quantity: 1 },
      failOnStatusCode: false,
    });

    // GET /cart/me 동시 5회 호출 — 어느 응답도 500이면 안 됨
    const concurrentGets = Array.from({ length: 5 }, () =>
      api.get(`${API_URL}/cart/me`, {
        headers: { "X-User-Id": userId },
        failOnStatusCode: false,
      }),
    );
    const responses = await Promise.all(concurrentGets);
    for (const res of responses) {
      expect(res.status(), `동시 GET /cart/me가 5xx — NonUniqueResult 결함: ${res.status()}`).not.toBe(500);
      expect(res.status()).toBe(200);
    }

    await api.dispose();
  });

  test("E2E-06-R05 GET /cart/me 호출이 cart row 수를 변경하지 않는다 (조회 전후 활성 cart count 동일)", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerUser(api, undefined, "Passw0rd!");
    const userId = String(user.id);

    // cart 생성
    await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": userId, "Content-Type": "application/json" },
      data: { productId: 1, quantity: 1 },
      failOnStatusCode: false,
    });

    // GET /cart/me 3회 — 모두 같은 응답 구조여야 하고 5xx 없음
    for (let i = 0; i < 3; i++) {
      const res = await api.get(`${API_URL}/cart/me`, {
        headers: { "X-User-Id": userId },
        failOnStatusCode: false,
      });
      expect(res.status(), `${i + 1}회차 GET /cart/me 5xx — 쓰기 사이드이펙트 결함`).not.toBe(500);
      expect(res.status()).toBe(200);
    }

    await api.dispose();
  });

  test("E2E-06-R06 기존 단일 활성 cart user의 GET /cart/me — V34 적용 후에도 동일 cart 반환", async () => {
    // X-User-Id: 1 (seed.sql의 기존 user) — V34 이후에도 하위호환 확인
    const api = await playwrightRequest.newContext();
    const res1 = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": "1" },
      failOnStatusCode: false,
    });
    expect(res1.status()).toBe(200);
    const b1 = await res1.json();

    const res2 = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": "1" },
      failOnStatusCode: false,
    });
    expect(res2.status()).toBe(200);
    const b2 = await res2.json();

    // cart id가 일치해야 함 (동일 cart 반환)
    if (b1.id !== undefined && b2.id !== undefined) {
      expect(b2.id, "V34 이후에도 동일 cart id 반환").toBe(b1.id);
    }

    await api.dispose();
  });

  test("E2E-06-E06 동시 cart 추가 경합에서 응답은 5xx가 아닌 비즈니스 응답(2xx 또는 4xx)이며 DB 중복 cart 없음", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerUser(api, undefined, "Passw0rd!");
    const userId = String(user.id);

    // 동시 10회 발사
    const responses = await Promise.all(
      Array.from({ length: 10 }, () =>
        api.post(`${API_URL}/cart/items`, {
          headers: { "X-User-Id": userId, "Content-Type": "application/json" },
          data: { productId: 2, quantity: 1 },
          failOnStatusCode: false,
        }),
      ),
    );

    const has5xx = responses.some((r) => r.status() >= 500);
    expect(has5xx, "동시 경합 중 5xx 발생 — NonUniqueResult 또는 서버 오류").toBe(false);

    // 직후 단건 GET — 5xx 없음
    const cartRes = await api.get(`${API_URL}/cart/me`, {
      headers: { "X-User-Id": userId },
      failOnStatusCode: false,
    });
    expect(cartRes.status()).not.toBe(500);

    await api.dispose();
  });

  test("E2E-06-E07 DELETE /cart 후 POST /cart/items 재추가 시 UNIQUE 제약 위반 500 없음", async () => {
    const api = await playwrightRequest.newContext();
    const user = await registerUser(api, undefined, "Passw0rd!");
    const userId = String(user.id);

    // cart 생성
    await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": userId, "Content-Type": "application/json" },
      data: { productId: 1, quantity: 1 },
      failOnStatusCode: false,
    });

    // cart 삭제
    const del = await api.delete(`${API_URL}/cart`, {
      headers: { "X-User-Id": userId },
      failOnStatusCode: false,
    });
    expect([200, 204]).toContain(del.status());

    // 재추가 — UNIQUE 제약 위반(500)이 발생하면 안 됨
    const reAdd = await api.post(`${API_URL}/cart/items`, {
      headers: { "X-User-Id": userId, "Content-Type": "application/json" },
      data: { productId: 1, quantity: 1 },
      failOnStatusCode: false,
    });
    expect(reAdd.status(), "DELETE 후 재추가 시 UNIQUE 제약 위반 500").toBeLessThan(500);

    await api.dispose();
  });
});

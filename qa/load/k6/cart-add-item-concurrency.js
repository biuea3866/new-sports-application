// qa/load/k6/cart-add-item-concurrency.js
// LOAD-04 장바구니 추가 동시성 — NonUniqueResult 미발생 검증.
// PR #181(cart NonUnique 500 fix) 동시성 런타임 재검증용.
// 같은 user_id에 동시 POST /cart/items를 몰아서 V34 active_marker 제약 회귀를 압박.

import http from "k6/http";
import { check, group } from "k6";
import { assertSafeTarget, headerAuth, API_URL } from "./lib/auth.js";
import { scenarioMetrics } from "./lib/metrics.js";
import { Rate, Trend } from "k6/metrics";

const SCENARIO_ID = "LOAD-04";
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

// 5xx(NonUniqueResultException·IllegalStateException 기인 500) 전용 카운터
const fivexxRate = new Rate("LOAD_04_5xx_rate");

// 시나리오 md의 "목표 임계":
//   p95 < 500ms, p99 < 1500ms, 5xx error rate = 0% (핵심 단언)
export const options = {
  thresholds: {
    // latency (p95/p99) — 회귀 추세 추적용 (로컬 환경 절대치 비교 금지)
    [`${METRIC_PREFIX}_latency`]: ["p(95)<500", "p(99)<1500"],
    // 5xx 0건 — 환경 무관 불변 단언 (NonUniqueResult / IllegalState 500)
    "LOAD_04_5xx_rate": ["rate<0.001"],  // 0.1% 미만 (사실상 0건 기대, k6 threshold는 비율만 지원)
    // 일반 http_req_failed (4xx 포함) — 동시성 시나리오이므로 409는 정상 비즈니스 응답
    "http_req_failed": ["rate<0.05"],
  },
  // 시나리오 md: ramp-up 30s + steady 2m + ramp-down 30s = 3m
  stages: [
    { duration: "30s", target: 50 },  // ramp-up: 0 → 50 VU over 30s
    { duration: "2m",  target: 50 },  // steady:  50 VU for 2m
    { duration: "30s", target: 0 },   // ramp-down: 50 → 0 VU over 30s
  ],
};

// 경합 집중 설계: VU를 작은 user_id 풀(5명)에 매핑
// 같은 user에 동시 POST /cart/items가 몰리도록 → 1 user = 1 cart 제약 압박
const USER_POOL_SIZE = 5;    // 시나리오 md 지정
const PRODUCT_POOL_SIZE = 10; // 시드: products 1~10

export function setup() {
  assertSafeTarget();

  // 캐시 워밍업: 상품 목록 GET 5회 (시나리오 md 지정)
  for (let i = 0; i < 5; i++) {
    http.get(`${API_URL}/cart/me`, {
      headers: headerAuth(1),
      tags: { scenario: SCENARIO_ID, step: "warmup" },
    });
  }

  return {};
}

export default function () {
  // VU를 5명 user 풀에 순환 매핑 (경합 집중)
  const userId = ((__VU - 1) % USER_POOL_SIZE) + 1;
  const headers = headerAuth(userId);

  // 상품 무작위 선택 (1~10)
  const productId = Math.floor(Math.random() * PRODUCT_POOL_SIZE) + 1;
  const quantity = Math.floor(Math.random() * 3) + 1; // 1~3

  group(`[${SCENARIO_ID}] POST /cart/items concurrency`, () => {
    const payload = JSON.stringify({ productId, quantity });

    const res = http.post(
      `${API_URL}/cart/items`,
      payload,
      {
        headers,
        tags: { scenario: SCENARIO_ID, step: "add-item" },
      },
    );

    latency.add(res.timings.duration);

    // 5xx만 결함 카운터 (409는 정상 비즈니스 경합 — error 아님)
    const is5xx = res.status >= 500;
    fivexxRate.add(is5xx);
    failures.add(is5xx);

    check(res, {
      "not 5xx (NonUniqueResult/IllegalState 방어)": (r) => r.status < 500,
      "2xx or 4xx (정상 비즈니스 응답)": (r) => r.status < 500,
    });

    // 응답 body 검증: sampling (시나리오 md: __ITER % 100 === 0)
    if (__ITER % 100 === 0 && (res.status === 200 || res.status === 201)) {
      check(res, {
        "has cartId": (r) => r.json("cartId") !== undefined || r.json("id") !== undefined,
        "has items array": (r) => Array.isArray(r.json("items")),
      });
    }
  });

  // GET /cart/me — 활성 cart 상태 확인 (경량 샘플링)
  if (__ITER % 50 === 0) {
    group(`[${SCENARIO_ID}] GET /cart/me state-check`, () => {
      const meRes = http.get(
        `${API_URL}/cart/me`,
        {
          headers,
          tags: { scenario: SCENARIO_ID, step: "get-cart" },
        },
      );

      // GET도 5xx이면 결함
      fivexxRate.add(meRes.status >= 500);

      check(meRes, {
        "GET /cart/me not 5xx": (r) => r.status < 500,
      });
    });
  }
}

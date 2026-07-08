// qa/load/k6/goods-limited-drop-spike.js
// LOAD-05 한정판 마케팅 이벤트 구매 spike — 20000 TPS 도전 목표(BE-12, 근거: TDD "Testing Plan",
// PRD Success Metrics·M4). ticket-seat-select-spike.js(LOAD-02) 하네스(lib/auth·lib/metrics) 재사용.
//
// related-files:
//   - backend/src/main/kotlin/com/sportsapp/presentation/goods/controller/LimitedDropApiController.kt
//   - backend/src/main/kotlin/com/sportsapp/domain/goods/service/LimitedDropDomainService.kt
// related-ticket: BE-12
// 사전 시드: qa/load/seeds/goods-limited-drop-spike.sql (product 9000001, stock 5,000,000)
//
// 20000 TPS는 "도전 목표"다(PRD) — 로컬 docker-compose 단일 인스턴스, Redis 세마포어 완충
// (semaphore-permits 기본 200), HikariCP 기본 커넥션 풀(기본 10)이라는 조건에서는 물리적으로
// 미달이 예상된다. 이 스크립트의 목적은 20000 TPS "달성"이 아니라, 도달 가능한 실제 TPS와
// 병목 지점(① Redis 입장 게이트 응답 지연 ② admissionSemaphore 완충 대기 ③ Stock.@Version
// 낙관적 락 경합 ④ HikariCP 커넥션 풀 고갈)을 계측·기록하는 것이다 — 목표 미달은 스크립트
// 실패가 아니라 관측 결과다.

import http from "k6/http";
import { check, fail, group } from "k6";
import { Counter } from "k6/metrics";
import { assertSafeTarget, headerAuth, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";

const SCENARIO_ID = "LOAD-05";
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

// 도달 TPS·병목 기록용 커스텀 메트릭(Observability, PRD Success Metrics).
// 종료 후 summary-export JSON에서 `LOAD_05_requests_total.count / 실제 테스트 경과초` = 도달 TPS.
const requestCounter = new Counter(`${METRIC_PREFIX}_requests_total`);
const acceptedCounter = new Counter(`${METRIC_PREFIX}_accepted_total`); // 202 성공
const rejectedCounter = new Counter(`${METRIC_PREFIX}_rejected_total`); // 409/425/403 정상 실패
const serverErrorCounter = new Counter(`${METRIC_PREFIX}_server_error_total`); // 5xx만(결함)

// 목표 임계 — p95 < 1000ms, p99 < 3000ms, 5xx error rate < 1%.
// 409(SoldOut)·425(TooEarly)·403(PerUserLimitExceeded)은 정상 비즈니스 응답이라
// failures(5xx 전용 Rate)·http_req_failed 판정에서 제외한다(headerAuth·status 분류, LOAD-02 선례).
export const options = {
  thresholds: thresholdsFor(METRIC_PREFIX, "complexPost", {
    p95: 1000,
    p99: 3000,
    errorRate: 0.01,
  }),
  // 단축 실행: LOAD-02(원 4m: warmup 30s + spike 30s + steady 2m + ramp-down 1m)와 동일 비율(1/5)로
  // warmup 6s + spike 6s + steady 24s + ramp-down 12s = 48s. VU 상한 3000은 로컬 환경에서 실행
  // 가능한 현실적 상한이며, 실제 20000 TPS 도달 여부는 http_reqs(k6 내장)·requestCounter로 사후 계측한다.
  stages: [
    { duration: "6s", target: 200 }, // warmup    (30s → 6s)
    { duration: "6s", target: 3000 }, // spike     (30s → 6s) — 20000 TPS 도전 구간
    { duration: "24s", target: 3000 }, // steady    (2m → 24s)
    { duration: "12s", target: 0 }, // ramp-down (1m → 12s)
  ],
};

const SEED_PRODUCT_ID = Number(__ENV.QA_LIMITED_DROP_PRODUCT_ID || 9000001);
const OWNER_USER_ID = Number(__ENV.QA_LIMITED_DROP_OWNER_ID || 9000000);
// limitedQuantity: steady 구간 유입량 규모로 산정해 "다수 성공 + 일부 SoldOut"인 현실적
// 마케팅 스파이크 트래픽(정상 실패가 섞인 혼합 응답)을 재현한다 — 무제한 재고는 스파이크
// 시나리오(오버셀·소진 경계 관측)의 취지에 맞지 않는다.
const LIMITED_QUANTITY = Number(__ENV.QA_LIMITED_DROP_QUANTITY || 50000);

export function setup() {
  assertSafeTarget();

  // 회차 개설 — 판매 시작 시각을 과거로 시드해 즉시 구매 가능 상태로 만든다(FR-2 게이트 우회 아님,
  // "이미 열린 회차"를 준비하는 것뿐).
  const openAt = new Date(Date.now() - 60_000).toISOString();
  const closeAt = new Date(Date.now() + 3_600_000).toISOString();

  const createRes = http.post(
    `${API_URL}/limited-drops`,
    JSON.stringify({
      productId: SEED_PRODUCT_ID,
      openAt,
      closeAt,
      limitedQuantity: LIMITED_QUANTITY,
      perUserLimit: 1,
    }),
    { headers: headerAuth(OWNER_USER_ID) },
  );
  check(createRes, { "drop opened (201)": (r) => r.status === 201 }) ||
    fail(
      `한정판 회차 개설 실패: status=${createRes.status} body=${createRes.body} ` +
      `(qa/load/seeds/goods-limited-drop-spike.sql 시드가 선행됐는지 확인)`
    );
  const dropId = createRes.json("dropId");

  // 캐시·커넥션 워밍업: 회차 상세 GET 5회 priming (LOAD-02 선례)
  for (let i = 0; i < 5; i++) {
    http.get(`${API_URL}/limited-drops/${dropId}`, { headers: headerAuth(1) });
  }

  return { dropId };
}

export default function (data) {
  // VU×ITER 조합으로 유니크 사용자를 근사한다 — perUserLimit=1이라 동일 사용자가 재요청하면
  // PerUserLimitExceeded(403, 정상 실패)로 집계돼 순수 처리량 측정이 왜곡된다.
  const userId = __VU * 1_000_000 + __ITER;
  const idempotencyKey = `${__VU}-${__ITER}-${Date.now()}`;

  group(`[${SCENARIO_ID}] POST /limited-drops/{dropId}/orders`, () => {
    const res = http.post(
      `${API_URL}/limited-drops/${data.dropId}/orders`,
      JSON.stringify({ quantity: 1 }),
      {
        headers: { ...headerAuth(userId), "Idempotency-Key": idempotencyKey },
        tags: { scenario: SCENARIO_ID },
      },
    );

    requestCounter.add(1);
    latency.add(res.timings.duration);

    // 5xx만 error로 집계 — 409/425/403은 정상 비즈니스 응답(threshold 판정에서 제외)
    const is5xx = res.status >= 500;
    failures.add(is5xx);

    if (res.status === 202) {
      acceptedCounter.add(1);
    } else if (res.status === 409 || res.status === 425 || res.status === 403) {
      rejectedCounter.add(1);
    } else if (is5xx) {
      serverErrorCounter.add(1);
    }

    check(res, {
      "not 5xx (20000 TPS 미달은 허용되나 5xx는 0이어야 한다)": (r) => r.status < 500,
    });

    // 응답 body 검증: sampling — 성공 시 orderId·PENDING 상태 확인 (LOAD-02 선례 패턴)
    if (res.status === 202 && __ITER % 200 === 0) {
      check(res, {
        "has orderId": (r) => r.json("orderId") !== undefined,
        "status PENDING": (r) => r.json("status") === "PENDING",
      });
    }
  });
}

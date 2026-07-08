// qa/load/k6/marketing-spike.js
// INFRA-07: 마케팅 스파이크 시나리오 — 20000 TPS 도전.
// 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-07-marketing-spike-20000tps.md
// 근거 TDD: TDD.md "형제 과제 접점"(③)·ADR-001(ramping-arrival-rate 채택)·FR-5.
//
// ramping-arrival-rate executor로 lib/diurnal.js#spikeStages()(0→20000 30초 내 → 2분
// steady → 1분 감쇠)를 재생한다. 대상은 ③ 마케팅 이벤트 고부하 대응이 배포한
// POST /limited-drops/{dropId}/orders. dropId는 이 스크립트가 개설하지 않는다 —
// provision.sh(INFRA-03)가 사전 개설해 .env.sim에 기록한 DROP_ID(env)를 그대로 쓴다
// (TDD "synthetic 격리 계약" — 마케팅 drop은 ③의 POST /limited-drops로 대량 수량 사전 개설).
//
// 20000 TPS는 "도전 목표"다(PRD/TDD Open Questions) — limited-drop의 JVM-local
// admissionSemaphore(기본 200 permits)가 실질 병목이며, 로컬 단일 인스턴스 환경에서는
// 물리적으로 미달이 예상된다. 목표 미달은 스크립트 실패가 아니라 lib/gapreport.js가
// 기록하는 관측 결과다(TDD "실패 경로"표: "실패로 중단 금지. handleSummary가 달성률·
// 병목(client vs server) 기록").
//
// related-files:
//   - backend/src/main/kotlin/com/sportsapp/presentation/goods/controller/LimitedDropApiController.kt
//   - backend/src/main/kotlin/com/sportsapp/domain/goods/service/LimitedDropDomainService.kt
// related-ticket: INFRA-07

import http from "k6/http";
import { check, group } from "k6";
import { Counter } from "k6/metrics";
import { assertSafeTarget, headerAuth, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";
import { spikeStages } from "./lib/diurnal.js";
import { syntheticUserId } from "./lib/pool.js";
import { handleSummary as buildGapSummary } from "./lib/gapreport.js";

const SCENARIO_ID = "INFRA-07";
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

// gap report(FR-8) 목표 피크 TPS — spikeStages()의 steady 구간 target(20000)과 일치해야 한다.
const TARGET_PEAK_TPS = 20000;

// 예약 트리거(FR-5, TDD "예약 트리거": "연속 곡선 내 startTime 오프셋 또는 reseed 컨테이너
// cron이 지정 시각에 기동"). 이 스크립트를 B2C 상시 곡선과 같은 k6 실행에 이어붙일 때는
// 그 실행 시작 시각으로부터의 오프셋(예: "3h")을 넘긴다. 단독 실행 시 기본값 "0s" —
// 즉시 스파이크 시작(예약은 이 경우 외부 스케줄러(cron)가 이 스크립트 자체의 기동 시각을
// 맞추는 방식으로 대신한다).
const SPIKE_START_TIME = __ENV.QA_SPIKE_START_TIME || "0s";

// 리소스 상한(NFR "재충전이 관측 대상에 스파이크 유발 금지"와 동일 취지 —
// k6 러너 리소스도 상한을 둬 측정 왜곡을 방지한다). k6-runner 컨테이너 CPU/메모리 상한
// (⑦ docker-compose.sim.yml, INFRA-08 소관)에 맞춰 env로 조정 가능하게 노출한다.
// maxVUs 도달·dropped_iterations>0은 스크립트 실패가 아니라 gapreport가
// "client(k6)" 병목으로 태깅한다(lib/gapreport.js#estimateBottleneck).
const PRE_ALLOCATED_VUS = Number(__ENV.QA_SPIKE_PRE_ALLOCATED_VUS || 1000);
const MAX_VUS = Number(__ENV.QA_SPIKE_MAX_VUS || 4000);

// 도달 TPS·병목 기록용 커스텀 메트릭(Observability) — LOAD-05(goods-limited-drop-spike.js) 선례.
const requestCounter = new Counter(`${METRIC_PREFIX}_requests_total`);
const acceptedCounter = new Counter(`${METRIC_PREFIX}_accepted_total`); // 202 성공
const rejectedCounter = new Counter(`${METRIC_PREFIX}_rejected_total`); // 409/425/429/403 정상 실패
const serverErrorCounter = new Counter(`${METRIC_PREFIX}_server_error_total`); // 5xx만(결함)

export const options = {
  scenarios: {
    marketingSpike: {
      executor: "ramping-arrival-rate",
      startTime: SPIKE_START_TIME,
      startRate: 0,
      timeUnit: "1s",
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      stages: spikeStages(), // 0→20000(30s) → steady(2m) → 감쇠(1m), lib/diurnal.js(INFRA-01)
      exec: "purchase",
    },
  },
  // 409(SoldOut)·425(TooEarly)·429(Throttled)·403(PerUserLimitExceeded)은 정상 비즈니스
  // 응답이라 failures(5xx 전용 Rate)·http_req_failed 판정에서 제외한다(purchase() 내 분류).
  thresholds: thresholdsFor(METRIC_PREFIX, "complexPost", {
    p95: 1000,
    p99: 3000,
    errorRate: 0.01,
  }),
};

export function setup() {
  assertSafeTarget();

  const dropId = __ENV.DROP_ID;
  if (!dropId) {
    throw new Error(
      "[INFRA-07] DROP_ID(env)가 없습니다. qa/load/provision/provision.sh(INFRA-03)를 먼저 " +
      "실행해 .env.sim에 DROP_ID를 기록하세요 — 이 스크립트는 한정판 회차를 직접 개설하지 않습니다."
    );
  }

  // 캐시·커넥션 워밍업: 회차 상세 GET 5회 priming (LOAD-05 선례)
  for (let i = 0; i < 5; i++) {
    http.get(`${API_URL}/limited-drops/${dropId}`, { headers: headerAuth(1) });
  }

  return { dropId };
}

export function purchase(data) {
  // synthetic 유저 풀(lib/pool.js, FR-9)을 재사용한다 — VU마다 새 유저를 만들지 않고
  // QA_USER_POOL_SIZE(기본 1000)만큼 순환. provision.sh의 DROP_PER_USER_LIMIT(기본
  // 1,000,000)이 이 재사용을 전제로 크게 잡혀 있어 1인 한도(403) 오탐이 나지 않는다.
  const userId = syntheticUserId(__VU);
  const idempotencyKey = `${SCENARIO_ID}-${__VU}-${__ITER}-${Date.now()}`;

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

    // 5xx만 error로 집계 — 202/409/425/429/403은 전부 정상 비즈니스 응답(TDD "실패 경로"표).
    const isServerError = res.status >= 500;
    failures.add(isServerError);

    if (res.status === 202) {
      acceptedCounter.add(1);
    } else if ([409, 425, 429, 403].includes(res.status)) {
      rejectedCounter.add(1);
    } else if (isServerError) {
      serverErrorCounter.add(1);
    }

    check(res, {
      "not 5xx (20000 TPS 미달은 허용되나 5xx는 0이어야 한다)": (r) => r.status < 500,
    });

    // 응답 body 검증: sampling — 성공 시 orderId·PENDING 상태 확인 (LOAD-05 선례 패턴)
    if (res.status === 202 && __ITER % 200 === 0) {
      check(res, {
        "has orderId": (r) => r.json("orderId") !== undefined,
        "status PENDING": (r) => r.json("status") === "PENDING",
      });
    }
  });
}

// FR-8 격차 리포트 — 달성률(actual http_reqs rate / TARGET_PEAK_TPS)과 병목(client vs server)을
// qa/load/results/INFRA-07-gap.json + stdout 요약으로 남긴다(lib/gapreport.js, INFRA-01).
export function handleSummary(data) {
  return buildGapSummary(data, { targetPeak: TARGET_PEAK_TPS, scenarioId: SCENARIO_ID });
}

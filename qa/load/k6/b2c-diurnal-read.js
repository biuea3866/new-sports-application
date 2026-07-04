// qa/load/k6/b2c-diurnal-read.js
// INFRA-04: B2C 일주기 조회(read) 곡선 — B2C 목표 TPS의 70%(peak 2100)를 조회로 발생시켜
// 소진성 자원(슬롯 capacity·재고·좌석 락)을 소비하지 않는 안정적인 배경 트래픽을 만든다.
//
// 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-04-b2c-diurnal-read-조회곡선.md
// 근거 TDD: ../TDD.md "곡선 배율표"·"인터페이스·계약"·Testing Plan.
//
// 조회 엔드포인트 가중 혼합(lib/readmix.js#READ_MIX, facility/product/event 3개 그룹):
//   GET /facilities?gu&type(permitAll), GET /facilities/{fid}/slots,
//   GET /products·/products/{id}·/products/popular, GET /events·/events/{id}.
// 대상 데이터: facilities?gu&type은 일반 Mongo 시설 카탈로그(facility-search.js GU_LIST/TYPE_LIST
// 관례 재사용), facility-slots·products·events는 INFRA-03 synthetic baseline
// (qa/load/seeds/simulator-baseline.sql: SYN-FAC-1~3, product id 9010001~9010010, event id 9000001).
//
// 곡선: lib/diurnal.js#b2cReadStages(timeScale) — peak 2100(=B2C 목표 3000×0.7).
// TIME_SCALE(env, 기본 1=실시간 24h)로 압축 실행 가능(diurnal.js 헤더 주석의 관례 계승).
// ramping-arrival-rate(open model)를 사용해 서버 지연과 무관하게 목표 도착률(TPS)을 그대로
// 추종 측정한다(TDD ADR-001 — ramping-vus는 closed model이라 채택하지 않음).

import http from "k6/http";
import { check, fail, group } from "k6";
import { assertSafeTarget, headerAuth, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";
import { b2cReadStages } from "./lib/diurnal.js";
import { syntheticUserId } from "./lib/pool.js";
import { pickReadEndpoint } from "./lib/readmix.js";
import { handleSummary as buildGapReportSummary } from "./lib/gapreport.js";

const SCENARIO_ID = "b2c-read";
// k6 metric name 제약(영문/숫자/밑줄만) — group·gap report 파일명은 SCENARIO_ID, 메트릭 이름은 normalize.
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

const TARGET_PEAK_RATE = 2100; // = B2C 목표 3000 × 0.7 (lib/diurnal.js#b2cReadStages와 동일 값)
const TIME_SCALE = Number(__ENV.TIME_SCALE) || 1; // 1=실시간 24h. 압축 검증은 예: TIME_SCALE=0.02
const READ_STAGES = b2cReadStages(TIME_SCALE);

// synthetic 대상 풀 (근거: qa/load/seeds/simulator-baseline.sql, provision.sh 문서화 값과 동일 기본값)
const SYN_FACILITY_IDS = (__ENV.QA_SYN_FACILITY_IDS || "SYN-FAC-1,SYN-FAC-2,SYN-FAC-3").split(",");
const SYN_PRODUCT_ID_START = Number(__ENV.QA_SYN_PRODUCT_ID_START || 9010001);
const SYN_PRODUCT_ID_END = Number(__ENV.QA_SYN_PRODUCT_ID_END || 9010010);
const SYN_EVENT_ID = __ENV.QA_SYN_EVENT_ID || "9000001";

// facilities?gu&type은 synthetic 전용이 아닌 일반 Mongo 시설 카탈로그 조회 — facility-search.js(LOAD-01)
// 선례의 목록을 재사용해 실제 검색 패턴을 근사한다.
const GU_LIST = ["강남구", "송파구", "마포구", "강서구", "노원구"];
const TYPE_LIST = ["풋살장", "농구장", "테니스장"];
const PRODUCT_CATEGORIES = ["EQUIPMENT", "APPAREL", "FOOTWEAR", "ACCESSORY"];

// ramping-arrival-rate 동시성 산정: peak 2100 rps × complexGet p95 목표(0.3s) ≈ 630 동시 요청.
// 응답 지연 시(server 병목) 대비 여유(약 5배)를 둬 VU 부족으로 인한 dropped_iterations가
// "client(k6) 한계"로 오분류되지 않게 한다(TDD "실패 경로" 표 — 병목 오분류 방지).
const PRE_ALLOCATED_VUS = Number(__ENV.QA_READ_PRE_ALLOCATED_VUS || 300);
const MAX_VUS = Number(__ENV.QA_READ_MAX_VUS || 3000);

export const options = {
  scenarios: {
    b2cDiurnalRead: {
      executor: "ramping-arrival-rate",
      exec: "default",
      timeUnit: "1s",
      // 곡선의 첫 keyframe(00시, multiplier=0.05)에 이미 도달한 상태로 시작 — 0에서부터
      // 인위적으로 ramp-up하지 않는다(심야 저부하가 "곡선의 시작점"이지 "정지 상태"가 아님).
      startRate: READ_STAGES[0].target,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      stages: READ_STAGES,
    },
  },
  thresholds: thresholdsFor(METRIC_PREFIX, "complexGet"),
};

function randomFrom(list) {
  return list[Math.floor(Math.random() * list.length)];
}

function randomProductId() {
  return SYN_PRODUCT_ID_START + Math.floor(Math.random() * (SYN_PRODUCT_ID_END - SYN_PRODUCT_ID_START + 1));
}

/** pickReadEndpoint()가 고른 엔드포인트에 맞는 실제 GET 요청을 수행한다. */
function performRead(endpoint, headers) {
  const tags = { scenario: SCENARIO_ID, endpoint: endpoint.name };
  switch (endpoint.name) {
    case "facilities-list": {
      const gu = randomFrom(GU_LIST);
      const type = randomFrom(TYPE_LIST);
      const url = `${API_URL}/facilities?gu=${encodeURIComponent(gu)}&type=${encodeURIComponent(type)}&page=0&size=50`;
      return http.get(url, { headers, tags });
    }
    case "facility-slots": {
      const facilityId = randomFrom(SYN_FACILITY_IDS);
      return http.get(`${API_URL}/facilities/${facilityId}/slots`, { headers, tags });
    }
    case "products-list":
      return http.get(`${API_URL}/products?page=0&size=20`, { headers, tags });
    case "product-detail": {
      const productId = randomProductId();
      return http.get(`${API_URL}/products/${productId}`, { headers, tags });
    }
    case "products-popular": {
      const category = randomFrom(PRODUCT_CATEGORIES);
      return http.get(`${API_URL}/products/popular?category=${category}`, { headers, tags });
    }
    case "events-list":
      return http.get(`${API_URL}/events?page=0&size=20`, { headers, tags });
    case "event-detail":
      return http.get(`${API_URL}/events/${SYN_EVENT_ID}`, { headers, tags });
    default:
      fail(`알 수 없는 조회 엔드포인트: ${endpoint.name}`);
      return undefined;
  }
}

export function setup() {
  assertSafeTarget();
  // 캐시·커넥션 워밍업 — 3개 그룹 대표 요청을 5회씩 priming(facility-search.js 선례 관례).
  const headers = headerAuth(1);
  for (let i = 0; i < 5; i++) {
    http.get(`${API_URL}/facilities?gu=${encodeURIComponent(GU_LIST[0])}&type=${encodeURIComponent(TYPE_LIST[0])}&page=0&size=50`, { headers });
    http.get(`${API_URL}/products?page=0&size=20`, { headers });
    http.get(`${API_URL}/events?page=0&size=20`, { headers });
  }
  return {};
}

export default function () {
  const endpoint = pickReadEndpoint();
  const headers = headerAuth(syntheticUserId(__VU));

  group(`[${SCENARIO_ID}] ${endpoint.name}`, () => {
    const res = performRead(endpoint, headers);

    latency.add(res.timings.duration);
    // 조회는 소진성 자원을 소비하지 않으므로 409 같은 정상 비즈니스 응답이 없다 — 5xx만 실패.
    failures.add(res.status >= 500);

    const ok = check(res, {
      "status 200": (r) => r.status === 200,
    });

    // 응답 body 검증은 sampling — 매 요청 파싱 비용을 피한다(facility-search.js 선례 관례).
    if (ok && __ITER % 100 === 0) {
      check(res, {
        "has body": (r) => r.body && r.body.length > 0,
      });
    }
  });
}

/** FR-8 격차 리포트 — qa/load/results/b2c-read-gap.json + stdout 요약. */
export function handleSummary(data) {
  return buildGapReportSummary(data, { targetPeak: TARGET_PEAK_RATE, scenarioId: SCENARIO_ID });
}

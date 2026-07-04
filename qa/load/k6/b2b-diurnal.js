// qa/load/k6/b2b-diurnal.js
// INFRA-06: B2B 파트너 일주기 곡선(peak 100TPS) — 상품·이벤트 등록 부하, 하루 누적 1,000건↑(FR-4).
// 근거 티켓: 프로젝트/스포츠앱/상시 트래픽 시뮬레이터/Tickets/INFRA-06-b2b-diurnal-곡선.md
// 근거 TDD: ../TDD.md "인터페이스·계약"·"형제 과제 접점"(②).
//
// ② B2B 파트너 연동이 발급한 `partner_<keyId>_<random>` API Key(Bearer)를 전 VU가 공유·재사용한다
// (FR-9 — 요청마다 재발급하지 않음). 이 키의 연동 User는 GOODS_SELLER·EVENT_HOST 두 역할을 함께
// 보유하므로(CreatePartnerUseCase#assignPartnerRoles) 동일 키로 두 엔드포인트 모두 호출 가능하다.
//
// 등록 혼합: POST /api/goods-seller/products, POST /api/event-host/events를 50:50 교대 호출한다
// (lib/b2bRegistration.js#selectRegistrationAction). 401(무효·폐기 키)·403(역할 없음)은 5xx가
// 아니므로 실패로 집계하지 않는다(goods-limited-drop-spike.js 관례 계승).

import http from "k6/http";
import { check, group } from "k6";
import { Counter } from "k6/metrics";
import { assertSafeTarget, API_URL } from "./lib/auth.js";
import { partnerAuthHeaders } from "./lib/pool.js";
import { b2bStages } from "./lib/diurnal.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";
import { buildGapReport, formatTextSummary } from "./lib/gapreport.js";
import {
  selectRegistrationAction,
  buildProductRegistrationRequest,
  buildEventRegistrationRequest,
  dailyEquivalentCount,
} from "./lib/b2bRegistration.js";

const SCENARIO_ID = "INFRA-06";
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

const TARGET_PEAK_TPS = 100; // lib/diurnal.js#b2bStages peak과 일치(FR-4)
const DAILY_TARGET_COUNT = 1000; // FR-4 하루 누적 등록 목표

// TIME_SCALE(env): 1=실시간 24h, 0.2=압축(기존 1/5 관례). 기본 1 — 상시 시뮬레이터는 실시간
// 곡선을 그대로 따르는 것이 정상 동작이고, 로컬 검증·CI 단축 실행에서만 override한다.
const TIME_SCALE = Number(__ENV.TIME_SCALE) || 1;

// peak 100 TPS 기준 VU 상한 — 등록 API 응답이 초 단위를 넘지 않는다는 전제로 여유 있게 산정.
const PRE_ALLOCATED_VUS = Number(__ENV.QA_B2B_PRE_ALLOCATED_VUS || 30);
const MAX_VUS = Number(__ENV.QA_B2B_MAX_VUS || 150);

const registeredCounter = new Counter(`${METRIC_PREFIX}_registered_total`); // 2xx만(성공 등록)
const rejectedCounter = new Counter(`${METRIC_PREFIX}_rejected_total`); // 401/403(무효 키·권한 없음, 정상 실패)
const serverErrorCounter = new Counter(`${METRIC_PREFIX}_server_error_total`); // 5xx만(결함)

export const options = {
  scenarios: {
    b2b_diurnal: {
      executor: "ramping-arrival-rate",
      startRate: 0,
      timeUnit: "1s",
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      stages: b2bStages(TIME_SCALE),
    },
  },
  // 5xx만 결함으로 판정 — 401(무효·폐기 키)·403(역할 없음)은 threshold 판정에서 제외.
  thresholds: thresholdsFor(METRIC_PREFIX, "complexPost", { errorRate: 0.01 }),
};

export function setup() {
  assertSafeTarget();
  return {};
}

export default function () {
  const action = selectRegistrationAction(__ITER);
  const headers = partnerAuthHeaders();

  if (action === "product") {
    registerProduct(headers);
  } else {
    registerEvent(headers);
  }
}

function registerProduct(headers) {
  group(`[${SCENARIO_ID}] POST /api/goods-seller/products`, () => {
    const payload = buildProductRegistrationRequest(__VU, __ITER);
    const res = http.post(`${API_URL}/api/goods-seller/products`, JSON.stringify(payload), {
      headers,
      tags: { scenario: SCENARIO_ID, action: "product" },
    });
    recordResult(res);
  });
}

function registerEvent(headers) {
  group(`[${SCENARIO_ID}] POST /api/event-host/events`, () => {
    const payload = buildEventRegistrationRequest(__VU, __ITER);
    const res = http.post(`${API_URL}/api/event-host/events`, JSON.stringify(payload), {
      headers,
      tags: { scenario: SCENARIO_ID, action: "event" },
    });
    recordResult(res);
  });
}

function recordResult(res) {
  latency.add(res.timings.duration);

  // 5xx만 error로 집계 — 401/403은 정상 비즈니스 응답(무효 키·권한 없음).
  const is5xx = res.status >= 500;
  failures.add(is5xx);

  if (res.status === 200 || res.status === 201) {
    registeredCounter.add(1);
  } else if (res.status === 401 || res.status === 403) {
    rejectedCounter.add(1);
  } else if (is5xx) {
    serverErrorCounter.add(1);
  }

  check(res, {
    "not 5xx (무효 키 401·권한 없음 403은 정상, 5xx만 결함)": (r) => r.status < 500,
  });
}

// FR-8 격차 리포트(lib/gapreport.js) + FR-4 하루 환산 등록 건수를 함께 기록한다.
export function handleSummary(data) {
  const gapReport = buildGapReport(data, { targetPeak: TARGET_PEAK_TPS, scenarioId: SCENARIO_ID });
  const registeredCount = data.metrics[`${METRIC_PREFIX}_registered_total`]?.values?.count || 0;
  const dailyEquivalent = dailyEquivalentCount(registeredCount, TIME_SCALE);

  const report = {
    ...gapReport,
    registeredCount,
    dailyEquivalentCount: dailyEquivalent,
    dailyEquivalentTarget: DAILY_TARGET_COUNT,
    dailyTargetMet: dailyEquivalent >= DAILY_TARGET_COUNT,
  };

  const textSummary = [
    formatTextSummary(report),
    `  registered count      : ${report.registeredCount}`,
    `  daily-equivalent count: ${report.dailyEquivalentCount} (target ${DAILY_TARGET_COUNT})`,
    `  daily target met      : ${report.dailyTargetMet}`,
  ].join("\n");

  return {
    [`qa/load/results/${SCENARIO_ID}-gap.json`]: JSON.stringify(report, null, 2),
    stdout: textSummary,
  };
}

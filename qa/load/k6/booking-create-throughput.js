// qa/load/k6/booking-create-throughput.js
// LOAD-03 시설 예약 생성 throughput.
// E2E: 슬롯 조회 → 예약 생성. /bookings 는 permitAll + X-User-Id 모델 — headerAuth 사용.

import http from "k6/http";
import { check, group } from "k6";
import { assertSafeTarget, headerAuth, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";

const SCENARIO_ID = "LOAD-03";
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

// 시나리오 md의 "목표 임계" — p95 < 500ms, p99 < 1500ms, error rate < 1% (5xx only)
export const options = {
  thresholds: thresholdsFor(METRIC_PREFIX, "complexPost", {
    p95: 500,
    p99: 1500,
    errorRate: 0.01,
  }),
  // 단축 실행: 시나리오 md 10m → 2m (1/5)
  stages: [
    { duration: "60s", target: 100 }, // ramp-up   (5m → 60s, 선형 증가)
    { duration: "36s", target: 100 }, // steady    (3m → 36s)
    { duration: "24s", target: 0 },   // ramp-down (2m → 24s)
  ],
};

// 시드 의존: 시설 50건, 시설당 슬롯 200건. 시설 ID range = 1~50, 슬롯 ID range = 1~10000
const FACILITY_POOL_SIZE = Number(__ENV.QA_FACILITY_POOL_SIZE || 50);
const SLOT_POOL_SIZE = Number(__ENV.QA_SLOT_POOL_SIZE || 10000);

export function setup() {
  assertSafeTarget();
  // 캐시 워밍업: 시설 목록 GET 5회 priming
  for (let i = 0; i < 5; i++) {
    http.get(`${API_URL}/facilities?page=0&size=50`, { headers: headerAuth(1) });
  }
  return {};
}

export default function () {
  // VU별 독립 사용자 ID — X-User-Id 헤더 기반 권한 모델
  const userId = __VU;
  const headers = headerAuth(userId);

  group(`[${SCENARIO_ID}] E2E slot list + booking create`, () => {
    // Step 1: 슬롯 조회 (사이드이펙트 없음)
    const facilityId = String(Math.floor(Math.random() * FACILITY_POOL_SIZE) + 1);
    const listRes = http.get(`${API_URL}/facilities/${facilityId}/slots`, {
      headers,
      tags: { scenario: SCENARIO_ID, step: "list-slots" },
    });
    if (listRes.status >= 500) {
      failures.add(true);
      latency.add(listRes.timings.duration);
      return;
    }

    // Step 2: 예약 생성 (단일 슬롯)
    const slotId = Math.floor(Math.random() * SLOT_POOL_SIZE) + 1;
    const payload = JSON.stringify({
      slotId: slotId,
      paymentMethod: "CARD",
      amount: 10000,
      currency: "KRW",
    });

    const createRes = http.post(
      `${API_URL}/bookings`,
      payload,
      { headers, tags: { scenario: SCENARIO_ID, step: "create-booking" } },
    );

    // E2E 전체 latency = 두 단계 합
    const totalDuration = listRes.timings.duration + createRes.timings.duration;
    latency.add(totalDuration);
    // 5xx만 error로 집계 (409는 슬롯 충돌 — 정상 비즈니스 응답)
    failures.add(createRes.status >= 500);

    const ok = check(createRes, {
      "create not 5xx": (r) => r.status < 500,
    });

    if (ok && __ITER % 100 === 0) {
      if (createRes.status === 202 || createRes.status === 200) {
        check(createRes, {
          "has bookingId": (r) => r.json("bookingId") !== undefined,
          "status PENDING or CONFIRMED": (r) => {
            const s = r.json("status");
            return s === "PENDING" || s === "CONFIRMED";
          },
        });
      }
    }
  });
}

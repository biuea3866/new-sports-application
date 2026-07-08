// qa/load/k6/ticket-seat-select-spike.js
// LOAD-02 경기 티켓 좌석 선택 spike.
// /events 는 permitAll + X-User-Id 헤더 모델이라 Bearer 토큰 불필요 — headerAuth 사용.

import http from "k6/http";
import { check, group } from "k6";
import { assertSafeTarget, headerAuth, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";

const SCENARIO_ID = "LOAD-02";
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

// 시나리오 md의 "목표 임계" — p95 < 1000ms, p99 < 3000ms, error rate < 2% (5xx only)
export const options = {
  thresholds: thresholdsFor(METRIC_PREFIX, "complexPost", {
    p95: 1000,
    p99: 3000,
    errorRate: 0.02,
  }),
  // 단축 실행: 시나리오 md 4m → 단축 비율 1/5 동일 적용 (48s)
  stages: [
    { duration: "6s",  target: 10 },   // warmup    (30s → 6s)
    { duration: "6s",  target: 200 },  // spike     (30s → 6s)
    { duration: "24s", target: 200 },  // steady    (2m → 24s)
    { duration: "12s", target: 0 },    // ramp-down (1m → 12s)
  ],
};

// 시나리오 md: 부하용 event 1건 + 좌석 5000석 (시드 의존)
const EVENT_ID = __ENV.QA_EVENT_ID || "1";
const SEAT_POOL_SIZE = Number(__ENV.QA_SEAT_POOL_SIZE || 5000);

export function setup() {
  assertSafeTarget();
  // 캐시 워밍업: event 상세 GET 5회 priming
  for (let i = 0; i < 5; i++) {
    http.get(`${API_URL}/events/${EVENT_ID}`, { headers: headerAuth(1) });
  }
  return {};
}

export default function () {
  // 좌석 ID 2개 무작위 선택 (시나리오 md: 평균 좌석 2개)
  const seatA = Math.floor(Math.random() * SEAT_POOL_SIZE) + 1;
  const seatB = ((seatA + Math.floor(Math.random() * (SEAT_POOL_SIZE - 1))) % SEAT_POOL_SIZE) + 1;
  const payload = JSON.stringify({ seatIds: [seatA, seatB] });

  // X-User-Id: VU별 독립 사용자 ID (BE는 X-User-Id 헤더 기반 권한)
  const headers = headerAuth(__VU);

  group(`[${SCENARIO_ID}] POST /events/{id}/seats/select`, () => {
    const res = http.post(
      `${API_URL}/events/${EVENT_ID}/seats/select`,
      payload,
      { headers, tags: { scenario: SCENARIO_ID } },
    );

    latency.add(res.timings.duration);
    // 5xx만 error로 집계 (409는 정상 비즈니스 응답 — 좌석 락 경쟁)
    failures.add(res.status >= 500);

    const ok = check(res, {
      "not 5xx": (r) => r.status < 500,
    });

    if (ok && __ITER % 50 === 0) {
      if (res.status === 200) {
        check(res, {
          "has lockId": (r) => r.json("lockId") !== undefined,
        });
      } else if (res.status === 409) {
        check(res, {
          "409 has error body": (r) => r.body && r.body.length > 0,
        });
      }
    }
  });
}

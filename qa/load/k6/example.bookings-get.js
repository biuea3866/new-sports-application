// qa/load/k6/example.bookings-get.js
// 예시 부하 시나리오 — GET /api/v1/bookings 목록 조회 latency 측정.
// qa-load-tester가 시나리오 md를 보고 이 패턴을 따라 신규 스크립트를 작성.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { assertSafeTarget, issueToken, authHeaders, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";

const SCENARIO_ID = "LOAD-EXAMPLE-01";
const { latency, failures } = scenarioMetrics(SCENARIO_ID);

export const options = {
  thresholds: thresholdsFor(SCENARIO_ID, "complexGet"),
  stages: [
    { duration: "1m", target: 20 },   // ramp-up
    { duration: "3m", target: 20 },   // steady
    { duration: "30s", target: 0 },   // ramp-down
  ],
};

export function setup() {
  assertSafeTarget();
  const token = issueToken();
  return { token };
}

export default function (data) {
  group(`[${SCENARIO_ID}] GET /api/v1/bookings`, () => {
    const res = http.get(`${API_URL}/api/v1/bookings?page=0&size=20`, {
      headers: authHeaders(data.token),
      tags: { scenario: SCENARIO_ID },
    });

    latency.add(res.timings.duration);
    failures.add(res.status !== 200);

    const ok = check(res, {
      "status 200": (r) => r.status === 200,
    });

    // 응답 body 검증은 sampling — 매 요청 JSON 파싱은 비용이 큼.
    if (ok && __ITER % 100 === 0) {
      check(res, {
        "has content array": (r) => Array.isArray(r.json("content")),
      });
    }
  });

  sleep(1);
}

// qa/load/k6/facility-search.js
// LOAD-01 시설 목록 검색 latency.
// 미인증 엔드포인트 — 토큰 발급 없이 GET /facilities 만 측정.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { assertSafeTarget, API_URL } from "./lib/auth.js";
import { scenarioMetrics, thresholdsFor } from "./lib/metrics.js";

const SCENARIO_ID = "LOAD-01";
// k6 metric name 제약 (영문/숫자/밑줄만) — group 표시는 SCENARIO_ID, 메트릭 이름은 normalize
const METRIC_PREFIX = SCENARIO_ID.replace(/-/g, "_");
const { latency, failures } = scenarioMetrics(METRIC_PREFIX);

// 시나리오 md의 "목표 임계" — p95 < 300ms, p99 < 800ms, error rate < 0.5%
export const options = {
  thresholds: thresholdsFor(METRIC_PREFIX, "complexGet", {
    p95: 300,
    p99: 800,
    errorRate: 0.005,
  }),
  // 단축 실행: 시나리오 md 5m → 1m (1/5)
  stages: [
    { duration: "12s", target: 30 },  // ramp-up (1m → 12s)
    { duration: "36s", target: 30 },  // steady  (3m → 36s)
    { duration: "12s", target: 0 },   // ramp-down (1m → 12s)
  ],
};

const GU_LIST = ["강남구", "송파구", "마포구", "강서구", "노원구"];
const TYPE_LIST = ["풋살장", "농구장", "테니스장"];

export function setup() {
  assertSafeTarget();
  // 캐시 워밍업 — 같은 쿼리 5회 호출
  for (let i = 0; i < 5; i++) {
    http.get(`${API_URL}/facilities?gu=${encodeURIComponent(GU_LIST[0])}&type=${encodeURIComponent(TYPE_LIST[0])}&page=0&size=50`);
  }
  return {};
}

export default function () {
  const gu = GU_LIST[Math.floor(Math.random() * GU_LIST.length)];
  const type = TYPE_LIST[Math.floor(Math.random() * TYPE_LIST.length)];
  const url = `${API_URL}/facilities?gu=${encodeURIComponent(gu)}&type=${encodeURIComponent(type)}&page=0&size=50`;

  group(`[${SCENARIO_ID}] GET /facilities`, () => {
    const res = http.get(url, {
      tags: { scenario: SCENARIO_ID },
    });

    latency.add(res.timings.duration);
    failures.add(res.status !== 200);

    const ok = check(res, {
      "status 200": (r) => r.status === 200,
    });

    // 응답 body 검증은 sampling (시나리오 md: __ITER % 100 === 0)
    if (ok && __ITER % 100 === 0) {
      check(res, {
        "has content array": (r) => Array.isArray(r.json("content")),
        "has totalElements": (r) => r.json("totalElements") !== undefined,
      });
    }
  });

  sleep(1);
}

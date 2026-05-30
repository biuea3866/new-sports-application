// qa/load/k6/lib/metrics.js
// 공통 임계·메트릭 헬퍼.

import { Trend, Rate } from "k6/metrics";

/**
 * 시나리오 ID별 latency·실패율 메트릭.
 * qa-defect-router가 summary.json에서 이 메트릭 이름으로 추적.
 */
export function scenarioMetrics(scenarioId) {
  return {
    latency: new Trend(`${scenarioId}_latency`, true),
    failures: new Rate(`${scenarioId}_failures`),
  };
}

/**
 * 엔드포인트별 기본 임계 추천값.
 * 시나리오 md의 "목표 임계" 표에서 override 가능.
 */
export const defaultThresholds = {
  simpleGet:    { p95: 100,  p99: 250,  errorRate: 0.001 },
  complexGet:   { p95: 300,  p99: 700,  errorRate: 0.005 },
  simplePost:   { p95: 500,  p99: 1000, errorRate: 0.005 },
  complexPost:  { p95: 1000, p99: 2000, errorRate: 0.01 },
  e2eFlow:     { p95: 3000, p99: 5000, errorRate: 0.01 },
};

/**
 * k6 options.thresholds 빌더.
 * 예: thresholdsFor("LOAD-01", "simpleGet")
 */
export function thresholdsFor(scenarioId, kind = "simpleGet", override = {}) {
  const base = { ...defaultThresholds[kind], ...override };
  return {
    [`${scenarioId}_latency`]: [`p(95)<${base.p95}`, `p(99)<${base.p99}`],
    [`${scenarioId}_failures`]: [`rate<${base.errorRate}`],
    http_req_failed: [`rate<${base.errorRate * 2}`],
  };
}

// INFRA-01 검증 — lib/gapreport.js (FR-8 격차 리포트)
//
// mock k6 summary data(data.metrics[name].values[field] 구조)를 handleSummary/
// estimateBottleneck에 주입해 병목 분류·달성률 계산을 검증한다.
//
// 근거: 티켓 "테스트 케이스" 6개 중 gapreport.js 관련 2개.
import { test } from "node:test";
import assert from "node:assert/strict";
import { handleSummary, estimateBottleneck, calculateAchievementRate } from "../gapreport.js";

function mockSummary({ droppedIterations, httpReqFailedRate, httpReqDurationP95, actualRate, vus, vusMax }) {
  return {
    metrics: {
      dropped_iterations: { values: { count: droppedIterations } },
      vus: { values: { value: vus } },
      vus_max: { values: { value: vusMax } },
      http_reqs: { values: { rate: actualRate } },
      http_req_failed: { values: { rate: httpReqFailedRate } },
      http_req_duration: { values: { "p(95)": httpReqDurationP95 } },
    },
  };
}

test("dropped_iterations>0인 mock summary는 병목이 client(k6)로 분류된다", () => {
  const data = mockSummary({
    droppedIterations: 5,
    httpReqFailedRate: 0.001,
    httpReqDurationP95: 200,
    actualRate: 1800,
    vus: 10,
    vusMax: 50,
  });

  assert.equal(estimateBottleneck(data), "client(k6)");

  const result = handleSummary(data, { targetPeak: 2100, scenarioId: "gap-test-client" });
  const reportJson = JSON.parse(result["qa/load/results/gap-test-client-gap.json"]);
  assert.equal(reportJson.bottleneck, "client(k6)");
});

test("5xx율이 높고 dropped_iterations가 0인 mock summary는 병목이 server로 분류된다", () => {
  const data = mockSummary({
    droppedIterations: 0,
    httpReqFailedRate: 0.05,
    httpReqDurationP95: 200,
    actualRate: 1800,
    vus: 10,
    vusMax: 50,
  });

  assert.equal(estimateBottleneck(data), "server");

  const result = handleSummary(data, { targetPeak: 2100, scenarioId: "gap-test-server" });
  const reportJson = JSON.parse(result["qa/load/results/gap-test-server-gap.json"]);
  assert.equal(reportJson.bottleneck, "server");
});

test("dropped_iterations=0·5xx율/p95 정상인 mock summary는 병목이 unknown이다", () => {
  const data = mockSummary({
    droppedIterations: 0,
    httpReqFailedRate: 0.001,
    httpReqDurationP95: 200,
    actualRate: 2100,
    vus: 10,
    vusMax: 50,
  });

  assert.equal(estimateBottleneck(data), "unknown");
});

test("달성률(%)은 실제 http_reqs rate / targetPeak로 계산된다", () => {
  const data = mockSummary({
    droppedIterations: 0,
    httpReqFailedRate: 0.001,
    httpReqDurationP95: 200,
    actualRate: 1050,
    vus: 10,
    vusMax: 50,
  });

  assert.equal(calculateAchievementRate(data, 2100), 50);
});

test("handleSummary는 stdout 텍스트와 gap.json 파일 내용을 함께 반환한다", () => {
  const data = mockSummary({
    droppedIterations: 0,
    httpReqFailedRate: 0.001,
    httpReqDurationP95: 200,
    actualRate: 2100,
    vus: 10,
    vusMax: 50,
  });

  const result = handleSummary(data, { targetPeak: 2100, scenarioId: "gap-test-full" });

  assert.ok("qa/load/results/gap-test-full-gap.json" in result);
  assert.ok(typeof result.stdout === "string" && result.stdout.includes("gap-test-full"));
});

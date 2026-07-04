// qa/load/k6/lib/gapreport.js
// INFRA-01 FR-8: 목표 대비 격차 리포트 — 달성률(%)·병목(client vs server) 추정.
//
// 근거: TDD "인터페이스·계약" — handleSummary(data, {targetPeak, scenarioId})가
// 달성률 = 실제 http_reqs rate / targetPeak, 병목 추정(dropped_iterations>0 또는
// vus==maxVUs → "client(k6)", 5xx율·p95 높음 → "server")을 계산해
// qa/load/results/<scenarioId>-gap.json + stdout 요약으로 남긴다(목표 TPS 미달은
// 실패가 아니라 관측 결과 — TDD "실패 경로" 표).

const HTTP_REQ_FAILED_RATE_THRESHOLD = 0.01; // 5xx·실패율 1% 초과 시 server 병목 후보
const HTTP_REQ_DURATION_P95_THRESHOLD_MS = 1000; // p95 1초 초과 시 server 병목 후보

/**
 * k6 summary data.metrics[name].values[field] 값을 안전하게 읽는다.
 * 메트릭이 활성화되지 않았거나(예: dropped_iterations 미발생) 값이 없으면 undefined.
 */
function metricValue(data, metricName, field) {
  const metric = data && data.metrics && data.metrics[metricName];
  if (!metric || !metric.values) return undefined;
  return metric.values[field];
}

/**
 * 병목 추정: 클라이언트(k6) 한계 신호가 우선한다.
 * - dropped_iterations > 0: k6가 목표 arrival rate를 소화하지 못해 반복을 버림
 * - 현재 vus가 vus_max에 도달: VU 상한으로 더 못 늘림
 * 두 신호가 없고 5xx율·p95가 임계 초과면 서버 병목으로 분류, 둘 다 아니면 "unknown".
 */
export function estimateBottleneck(data) {
  const droppedIterations = metricValue(data, "dropped_iterations", "count") || 0;
  const currentVus = metricValue(data, "vus", "value") || 0;
  const maxVus = metricValue(data, "vus_max", "value") || 0;
  if (droppedIterations > 0 || (maxVus > 0 && currentVus >= maxVus)) {
    return "client(k6)";
  }

  const httpReqFailedRate = metricValue(data, "http_req_failed", "rate") || 0;
  const httpReqDurationP95 = metricValue(data, "http_req_duration", "p(95)") || 0;
  if (httpReqFailedRate > HTTP_REQ_FAILED_RATE_THRESHOLD || httpReqDurationP95 > HTTP_REQ_DURATION_P95_THRESHOLD_MS) {
    return "server";
  }

  return "unknown";
}

/** 달성률(%) = 실제 http_reqs rate / targetPeak × 100. targetPeak 미지정 시 0. */
export function calculateAchievementRate(data, targetPeak) {
  const actualRate = metricValue(data, "http_reqs", "rate") || 0;
  if (!targetPeak) return 0;
  return Number(((actualRate / targetPeak) * 100).toFixed(2));
}

/** gap report 본문(JSON 직렬화 대상)을 구성한다. */
export function buildGapReport(data, { targetPeak, scenarioId }) {
  return {
    scenarioId,
    targetPeak,
    actualRate: metricValue(data, "http_reqs", "rate") || 0,
    achievementRate: calculateAchievementRate(data, targetPeak),
    bottleneck: estimateBottleneck(data),
    droppedIterations: metricValue(data, "dropped_iterations", "count") || 0,
    httpReqFailedRate: metricValue(data, "http_req_failed", "rate") || 0,
    httpReqDurationP95Ms: metricValue(data, "http_req_duration", "p(95)") || 0,
    generatedAt: new Date().toISOString(),
  };
}

/** gap report를 사람이 읽는 stdout 텍스트로 포맷한다. */
export function formatTextSummary(report) {
  return [
    `[gap-report] scenario=${report.scenarioId}`,
    `  target peak(TPS)    : ${report.targetPeak}`,
    `  actual rate(TPS)    : ${report.actualRate}`,
    `  achievement rate    : ${report.achievementRate}%`,
    `  bottleneck          : ${report.bottleneck}`,
    `  dropped_iterations  : ${report.droppedIterations}`,
    `  http_req_failed rate: ${report.httpReqFailedRate}`,
    `  http_req_duration p95(ms): ${report.httpReqDurationP95Ms}`,
  ].join("\n");
}

/**
 * k6 handleSummary 훅. qa/load/results/<scenarioId>-gap.json 파일과 stdout 텍스트
 * 요약을 함께 반환한다(k6 handleSummary 반환 규약: {[파일경로]: 내용, stdout: 텍스트}).
 *
 * @param {object} data k6가 전달하는 summary data(http_reqs·dropped_iterations 등 metrics 포함)
 * @param {{targetPeak:number, scenarioId:string}} context 목표 피크 TPS·시나리오 ID
 */
export function handleSummary(data, { targetPeak, scenarioId }) {
  const report = buildGapReport(data, { targetPeak, scenarioId });
  return {
    [`qa/load/results/${scenarioId}-gap.json`]: JSON.stringify(report, null, 2),
    stdout: formatTextSummary(report),
  };
}

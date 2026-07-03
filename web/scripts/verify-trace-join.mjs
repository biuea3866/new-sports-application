/**
 * [FE-03] web↔BE trace 합류 검증 스크립트
 *
 * 근거: design-fe-web.md "Testing Plan" scenario(E2E/수동) · PRD Success Metrics(연결율 99%, 10건 중 9건+).
 *
 * web(BFF)에 임의 요청 N건을 보낸 뒤, OTel Collector→Tempo에서 web span(service.name=sports-web)과
 * BE span(service.name=sports-application)이 동일 trace_id로 조회되는지 확인한다.
 *
 * 전제 (실 스택 필요 — docker-compose.observability.yml 기동):
 *   - web 서버가 기동돼 있고 OTEL_EXPORTER_OTLP_ENDPOINT가 설정돼 있어야 한다.
 *   - BE 서버가 기동돼 있고 MANAGEMENT_OTLP_TRACING_ENDPOINT가 설정돼 있어야 한다.
 *   - Tempo가 기동돼 있어야 한다(기본 :3200).
 *
 * 실 스택이 없는 환경(로컬 유닛 실행)에서는 Tempo 접근 실패를 감지해 exit(2)로 명확히 구분하고,
 * 수동 검증 절차는 `scripts/verify-trace-join.md`를 참고하도록 안내한다.
 *
 * 사용: node scripts/verify-trace-join.mjs
 * 환경변수:
 *   WEB_BASE_URL              기본 http://localhost:3000
 *   TARGET_PATH                기본 /api/health (BE 호출을 경유하는 Route)
 *   TEMPO_BASE_URL              기본 http://localhost:3200
 *   REQUEST_COUNT               기본 10
 *   WEB_SERVICE_NAME             기본 sports-web
 *   BE_SERVICE_NAME               기본 sports-application
 *   CONNECTION_RATE_THRESHOLD     기본 0.9 (NFR: 10건 중 9건 이상)
 *   EXPORT_WAIT_MS                기본 3000 (span export 대기)
 */

const WEB_BASE_URL = process.env["WEB_BASE_URL"] ?? "http://localhost:3000";
const TARGET_PATH = process.env["TARGET_PATH"] ?? "/api/health";
const TEMPO_BASE_URL = process.env["TEMPO_BASE_URL"] ?? "http://localhost:3200";
const REQUEST_COUNT = Number(process.env["REQUEST_COUNT"] ?? "10");
const WEB_SERVICE_NAME = process.env["WEB_SERVICE_NAME"] ?? "sports-web";
const BE_SERVICE_NAME = process.env["BE_SERVICE_NAME"] ?? "sports-application";
const CONNECTION_RATE_THRESHOLD = Number(process.env["CONNECTION_RATE_THRESHOLD"] ?? "0.9");
const EXPORT_WAIT_MS = Number(process.env["EXPORT_WAIT_MS"] ?? "3000");

/** @param {number} ms */
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 지정한 개수만큼 target URL에 순차 GET 요청을 보낸다. 실패해도 계속 진행한다(장애 격리 검증 대상 아님, 순수 부하 생성). */
async function sendRequests(count) {
  const targetUrl = `${WEB_BASE_URL}${TARGET_PATH}`;
  let succeeded = 0;

  for (let index = 0; index < count; index += 1) {
    try {
      const response = await fetch(targetUrl, { cache: "no-store" });
      if (response.ok) {
        succeeded += 1;
      }
    } catch (error) {
      console.warn(`[verify-trace-join] 요청 ${index + 1}/${count} 실패:`, error instanceof Error ? error.message : error);
    }
  }

  return succeeded;
}

/**
 * Tempo search API로 service.name 태그 기준 trace_id 목록을 조회한다.
 * @param {string} serviceName
 * @param {number} startUnixSec
 * @param {number} endUnixSec
 */
async function searchTraceIds(serviceName, startUnixSec, endUnixSec) {
  const searchUrl = new URL("/api/search", TEMPO_BASE_URL);
  searchUrl.searchParams.set("tags", `service.name=${serviceName}`);
  searchUrl.searchParams.set("start", String(startUnixSec));
  searchUrl.searchParams.set("end", String(endUnixSec));

  const response = await fetch(searchUrl.toString());
  if (!response.ok) {
    throw new Error(`Tempo search 실패: ${response.status} ${response.statusText}`);
  }

  /** @type {{ traces?: Array<{ traceID: string }> }} */
  const body = await response.json();
  return new Set((body.traces ?? []).map((trace) => trace.traceID));
}

/** 조인된 trace 하나를 조회해 두 서비스 span의 deployment.environment 태그 일치를 확인한다. */
async function verifyEnvironmentTagConsistency(traceId) {
  const response = await fetch(new URL(`/api/traces/${traceId}`, TEMPO_BASE_URL).toString());
  if (!response.ok) {
    return { consistent: false, reason: `trace 조회 실패: ${response.status}` };
  }

  const body = await response.json();
  const batches = body.batches ?? [];
  const environmentByService = new Map();

  for (const batch of batches) {
    const attributes = batch.resource?.attributes ?? [];
    const serviceNameAttr = attributes.find((attr) => attr.key === "service.name");
    const envAttr = attributes.find((attr) => attr.key === "deployment.environment");
    if (serviceNameAttr && envAttr) {
      environmentByService.set(serviceNameAttr.value?.stringValue, envAttr.value?.stringValue);
    }
  }

  const values = new Set(environmentByService.values());
  return { consistent: values.size <= 1, environmentByService };
}

async function main() {
  console.log(`[verify-trace-join] ${REQUEST_COUNT}건 요청 → ${WEB_BASE_URL}${TARGET_PATH}`);
  const succeeded = await sendRequests(REQUEST_COUNT);
  console.log(`[verify-trace-join] 요청 완료: ${succeeded}/${REQUEST_COUNT} 성공`);

  const endUnixSec = Math.floor(Date.now() / 1000) + 1;
  console.log(`[verify-trace-join] span export 대기 ${EXPORT_WAIT_MS}ms...`);
  await sleep(EXPORT_WAIT_MS);
  const startUnixSec = endUnixSec - Math.ceil((REQUEST_COUNT * 1000 + EXPORT_WAIT_MS) / 1000) - 30;

  let webTraceIds;
  let beTraceIds;
  try {
    [webTraceIds, beTraceIds] = await Promise.all([
      searchTraceIds(WEB_SERVICE_NAME, startUnixSec, endUnixSec),
      searchTraceIds(BE_SERVICE_NAME, startUnixSec, endUnixSec),
    ]);
  } catch (error) {
    console.error("[verify-trace-join] Tempo 접근 실패 — 실 스택(Collector/Tempo)이 기동돼 있는지 확인하세요.");
    console.error(error instanceof Error ? error.message : error);
    console.error("[verify-trace-join] 수동 검증 절차는 scripts/verify-trace-join.md 를 참고하세요.");
    process.exit(2);
    return;
  }

  const joinedTraceIds = [...webTraceIds].filter((traceId) => beTraceIds.has(traceId));
  const connectionRate = REQUEST_COUNT > 0 ? joinedTraceIds.length / REQUEST_COUNT : 0;

  console.log(`[verify-trace-join] web trace 수: ${webTraceIds.size}, BE trace 수: ${beTraceIds.size}`);
  console.log(`[verify-trace-join] 합류(joined) trace 수: ${joinedTraceIds.length}/${REQUEST_COUNT} (연결율 ${(connectionRate * 100).toFixed(1)}%)`);

  if (joinedTraceIds.length > 0) {
    const sampleTraceId = joinedTraceIds[0];
    const envCheck = await verifyEnvironmentTagConsistency(sampleTraceId);
    console.log(
      `[verify-trace-join] env 태그 일관성(sample trace=${sampleTraceId}): ${envCheck.consistent ? "일치" : "불일치"}`
    );
    if (!envCheck.consistent) {
      console.log(envCheck.environmentByService ?? envCheck.reason);
    }
  }

  if (connectionRate < CONNECTION_RATE_THRESHOLD) {
    console.error(
      `[verify-trace-join] 연결율 ${(connectionRate * 100).toFixed(1)}% 가 기준(${(CONNECTION_RATE_THRESHOLD * 100).toFixed(0)}%) 미만입니다.`
    );
    process.exit(1);
    return;
  }

  console.log("[verify-trace-join] 검증 통과.");
}

main();

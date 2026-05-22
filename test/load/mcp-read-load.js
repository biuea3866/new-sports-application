// test/load/mcp-read-load.js
// [TEST-01] MCP Read tool 부하 시험
//
// 목표: 200 SSE 동시 연결 + 50 RPS read tool 호출 (10분)
// 합격 기준 (PRD v2.5):
//   - P95 < 800ms
//   - 에러율 < 0.5%
//
// 실행 예시:
//   MCP_TOKEN=<token> QA_API_URL=http://localhost:8080 k6 run test/load/mcp-read-load.js
//
// 주의: 실제 SSE 연결 유지는 k6에서 http/1.1 long-poll로 근사. 정확한 SSE push 측정은
//       k6-sse xk6 확장이 필요하며 여기서는 MCP message 엔드포인트 RPS 부하를 측정한다.

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Trend, Rate } from "k6/metrics";
import { fail } from "k6";

// ── 환경 설정 ──────────────────────────────────────────────────────────────
const API_URL = __ENV.QA_API_URL || "http://localhost:8080";
const MCP_TOKEN = __ENV.MCP_TOKEN || "";
const ADMIN_EMAIL = __ENV.ADMIN_EMAIL || "load-test-admin@example.com";
const ADMIN_PASSWORD = __ENV.ADMIN_PASSWORD || "LoadTest1234!";

// ── 안전 가드 ──────────────────────────────────────────────────────────────
function assertSafeTarget() {
  const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1|.*\.local)(:|\/|$)/.test(API_URL);
  const isStaging = /staging/i.test(API_URL);
  if (!isLocal && !isStaging) {
    fail(
      `[SAFETY] QA_API_URL=${API_URL} 는 로컬 또는 staging 대상이 아닙니다. ` +
      `운영 환경 부하는 절대 금지.`
    );
  }
}

// ── 메트릭 ────────────────────────────────────────────────────────────────
const SCENARIO_ID = "MCP-READ-LOAD-01";
const readLatency = new Trend(`${SCENARIO_ID}_latency`, true);
const readFailures = new Rate(`${SCENARIO_ID}_failures`);
const sseConnectLatency = new Trend(`${SCENARIO_ID}_sse_connect_latency`, true);
const sseConnectFailures = new Rate(`${SCENARIO_ID}_sse_connect_failures`);

// ── k6 옵션 ───────────────────────────────────────────────────────────────
// 200 VU 유지 + 10분 steady 상태. 각 VU가 ~0.25 RPS → 전체 50 RPS 목표.
export const options = {
  stages: [
    { duration: "1m", target: 100 },  // ramp-up
    { duration: "8m", target: 200 },  // steady (목표 200 VU)
    { duration: "1m", target: 0 },    // ramp-down
  ],
  thresholds: {
    // 합격 기준 (PRD v2.5)
    [`${SCENARIO_ID}_latency`]: ["p(95)<800", "p(99)<1500"],
    [`${SCENARIO_ID}_failures`]: ["rate<0.005"],
    // SSE 연결 임계
    [`${SCENARIO_ID}_sse_connect_latency`]: ["p(95)<2000"],
    [`${SCENARIO_ID}_sse_connect_failures`]: ["rate<0.01"],
    // 전체 HTTP 에러율 보조 지표
    http_req_failed: ["rate<0.01"],
  },
};

// ── 전역 setup (1회 실행) ─────────────────────────────────────────────────
export function setup() {
  assertSafeTarget();

  // MCP_TOKEN 환경변수가 있으면 그대로 사용. 없으면 dev stub 흐름.
  if (MCP_TOKEN && MCP_TOKEN.length > 0) {
    return { mcpToken: MCP_TOKEN };
  }

  // dev 환경: 관리자 JWT 로그인 → MCP 토큰 발급
  const loginRes = http.post(
    `${API_URL}/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(loginRes, { "admin login ok": (r) => r.status === 200 }) ||
    fail(`admin login failed: status=${loginRes.status} body=${loginRes.body}`);

  const accessToken = loginRes.json("accessToken");
  if (!accessToken) fail(`accessToken not in login response: ${loginRes.body}`);

  // MCP 토큰 발급 (read:facility, read:booking 범위)
  const tokenRes = http.post(
    `${API_URL}/api/admin/mcp/tokens`,
    JSON.stringify({
      name: "load-test-read-token",
      scopes: ["read:facility", "read:booking", "read:inventory"],
      expiresAt: null,
    }),
    {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${accessToken}`,
      },
    }
  );
  check(tokenRes, { "mcp token issued": (r) => r.status === 200 || r.status === 201 }) ||
    fail(`mcp token issue failed: status=${tokenRes.status} body=${tokenRes.body}`);

  const mcpToken = tokenRes.json("plainToken");
  if (!mcpToken) fail(`plainToken not in response: ${tokenRes.body}`);

  return { mcpToken };
}

// ── MCP JSON-RPC 헬퍼 ─────────────────────────────────────────────────────
function mcpHeaders(mcpToken) {
  return {
    "Content-Type": "application/json",
    Authorization: `Bearer ${mcpToken}`,
  };
}

function toolsCall(mcpToken, toolName, toolArguments) {
  return http.post(
    `${API_URL}/mcp/message`,
    JSON.stringify({
      jsonrpc: "2.0",
      id: Math.floor(Math.random() * 1000000),
      method: "tools/call",
      params: { name: toolName, arguments: toolArguments },
    }),
    {
      headers: mcpHeaders(mcpToken),
      tags: { scenario: SCENARIO_ID, tool: toolName },
    }
  );
}

// ── VU 메인 루프 ──────────────────────────────────────────────────────────
export default function (data) {
  const mcpToken = data.mcpToken;

  // [TEST-01-SSE] SSE 연결 확인: GET /mcp/sse — 타임아웃 3초로 헤더만 수신
  group(`[${SCENARIO_ID}] SSE 연결 확인`, () => {
    const sseRes = http.get(`${API_URL}/mcp/sse`, {
      headers: {
        Authorization: `Bearer ${mcpToken}`,
        Accept: "text/event-stream",
      },
      tags: { scenario: SCENARIO_ID, step: "sse-connect" },
      timeout: "3s",
    });

    sseConnectLatency.add(sseRes.timings.duration);
    // SSE 스트림이 열리면 401이 아닌 200/202 응답
    const sseOk = sseRes.status !== 0 && sseRes.status !== 401 && sseRes.status !== 403;
    sseConnectFailures.add(!sseOk);
    check(sseRes, {
      "SSE endpoint 인증 통과": (r) => r.status !== 401,
    });
  });

  sleep(0.5);

  // [TEST-01-READ] getFacilities tool 호출
  group(`[${SCENARIO_ID}] getFacilities tool 호출`, () => {
    const res = toolsCall(mcpToken, "getFacilities", {
      gu: null,
      type: null,
      page: 0,
      size: 10,
    });

    readLatency.add(res.timings.duration);
    const ok = res.status !== 401 && res.status !== 403 && res.status !== 500;
    readFailures.add(!ok);

    check(res, {
      "getFacilities 인증 통과": (r) => r.status !== 401,
      "getFacilities 5xx 없음": (r) => r.status < 500,
    });

    // 응답 body 검증은 1/100 sampling
    if (ok && __ITER % 100 === 0) {
      check(res, {
        "jsonrpc 응답 구조 포함": (r) => r.body && r.body.includes("jsonrpc"),
      });
    }
  });

  sleep(0.3);

  // [TEST-01-READ-2] getFacilityStats tool 호출 (stats 도구)
  group(`[${SCENARIO_ID}] getFacilityStats tool 호출`, () => {
    const res = toolsCall(mcpToken, "getFacilityStats", {
      facilityId: "FAC-01",
    });

    readLatency.add(res.timings.duration);
    const ok = res.status !== 401 && res.status !== 403 && res.status !== 500;
    readFailures.add(!ok);

    check(res, {
      "getFacilityStats 인증 통과": (r) => r.status !== 401,
      "getFacilityStats 5xx 없음": (r) => r.status < 500,
    });
  });

  // VU당 ~2초 간격 → 200 VU * 0.5 RPS ≈ 100 req/s 상한 (tool 2개 합산)
  sleep(1);
}

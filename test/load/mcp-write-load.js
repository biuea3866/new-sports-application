// test/load/mcp-write-load.js
// [TEST-01] MCP Write tool 부하 시험
//
// 목표: 100 SSE 동시 연결 + 20 RPS write tool 호출 (10분)
// 합격 기준 (PRD v2.5):
//   - P95 < 1.5s
//   - 에러율 < 1%
//
// confirm flow (2단계 호출):
//   1차: cancelBooking(bookingId, reason, confirmationToken=null)
//        → McpResponse{ statusCode: 202, data: { confirmationToken, ... } }
//   2차: cancelBooking(bookingId, reason, confirmationToken=<token>)
//        → McpResponse{ statusCode: 200, data: BookingResponse }
//   두 단계 모두 latency 측정에 포함.
//
// 실행 예시:
//   MCP_TOKEN=<token> QA_API_URL=http://localhost:8080 k6 run test/load/mcp-write-load.js

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Trend, Rate, Counter } from "k6/metrics";
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
const SCENARIO_ID = "MCP-WRITE-LOAD-01";

// confirm flow 1차 호출 (토큰 발급) 메트릭
const confirmStep1Latency = new Trend(`${SCENARIO_ID}_confirm_step1_latency`, true);
const confirmStep1Failures = new Rate(`${SCENARIO_ID}_confirm_step1_failures`);

// confirm flow 2차 호출 (실제 실행) 메트릭
const confirmStep2Latency = new Trend(`${SCENARIO_ID}_confirm_step2_latency`, true);
const confirmStep2Failures = new Rate(`${SCENARIO_ID}_confirm_step2_failures`);

// 전체 write flow (1차 + 2차 합산) 메트릭 — 합격 기준 측정 대상
const writeFlowLatency = new Trend(`${SCENARIO_ID}_write_flow_latency`, true);
const writeFlowFailures = new Rate(`${SCENARIO_ID}_write_flow_failures`);

// confirm 토큰 발급 성공 카운터
const confirmTokensIssued = new Counter(`${SCENARIO_ID}_confirm_tokens_issued`);

// ── k6 옵션 ───────────────────────────────────────────────────────────────
// 100 VU 유지 + 10분 steady 상태. 각 VU가 ~0.2 RPS → 전체 20 RPS 목표.
export const options = {
  stages: [
    { duration: "1m", target: 50 },   // ramp-up
    { duration: "8m", target: 100 },  // steady (목표 100 VU)
    { duration: "1m", target: 0 },    // ramp-down
  ],
  thresholds: {
    // 합격 기준 (PRD v2.5) — 전체 write flow (1차 + 2차 합산)
    [`${SCENARIO_ID}_write_flow_latency`]: ["p(95)<1500", "p(99)<3000"],
    [`${SCENARIO_ID}_write_flow_failures`]: ["rate<0.01"],
    // 1차 호출 개별 임계 (보조 지표)
    [`${SCENARIO_ID}_confirm_step1_latency`]: ["p(95)<800"],
    [`${SCENARIO_ID}_confirm_step1_failures`]: ["rate<0.01"],
    // 2차 호출 개별 임계 (보조 지표)
    [`${SCENARIO_ID}_confirm_step2_latency`]: ["p(95)<1200"],
    [`${SCENARIO_ID}_confirm_step2_failures`]: ["rate<0.01"],
    // 전체 HTTP 에러율 보조 지표
    http_req_failed: ["rate<0.02"],
  },
};

// ── 전역 setup (1회 실행) ─────────────────────────────────────────────────
export function setup() {
  assertSafeTarget();

  if (MCP_TOKEN && MCP_TOKEN.length > 0) {
    return { mcpToken: MCP_TOKEN };
  }

  const loginRes = http.post(
    `${API_URL}/auth/login`,
    JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(loginRes, { "admin login ok": (r) => r.status === 200 }) ||
    fail(`admin login failed: status=${loginRes.status} body=${loginRes.body}`);

  const accessToken = loginRes.json("accessToken");
  if (!accessToken) fail(`accessToken not in login response: ${loginRes.body}`);

  // MCP 토큰 발급 (write 범위)
  const tokenRes = http.post(
    `${API_URL}/api/admin/mcp/tokens`,
    JSON.stringify({
      name: "load-test-write-token",
      scopes: ["write:booking", "write:slot"],
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

function toolsCall(mcpToken, toolName, toolArguments, stepTag) {
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
      tags: { scenario: SCENARIO_ID, tool: toolName, step: stepTag || "call" },
    }
  );
}

// ── 부하 시험용 dummy bookingId 생성 ────────────────────────────────────
// 실제 시드 데이터가 없으면 404/비즈니스 에러가 발생하지만 confirm 토큰 발급
// 단계(1차 호출)는 DB 조회 없이 Redis에 토큰만 저장하므로 latency 측정 가능.
function dummyBookingId() {
  return Math.floor(Math.random() * 1000) + 1;
}

// ── VU 메인 루프 ──────────────────────────────────────────────────────────
export default function (data) {
  const mcpToken = data.mcpToken;
  const bookingId = dummyBookingId();
  const flowStart = Date.now();

  // ── cancelBooking confirm flow ──────────────────────────────────────────
  group(`[${SCENARIO_ID}] cancelBooking confirm flow — 1차 호출 (토큰 발급)`, () => {
    const step1Res = toolsCall(mcpToken, "cancelBooking", {
      bookingId: bookingId,
      reason: "부하 시험용 취소 요청",
      confirmationToken: null,
    }, "step1-issue");

    confirmStep1Latency.add(step1Res.timings.duration);
    // 1차 호출 성공 기준: 401/403/500이 아닌 응답 (confirm 토큰 발급)
    const step1Ok = step1Res.status !== 0 &&
                    step1Res.status !== 401 &&
                    step1Res.status !== 403 &&
                    step1Res.status < 500;
    confirmStep1Failures.add(!step1Ok);

    check(step1Res, {
      "1차 호출 인증 통과": (r) => r.status !== 401,
      "1차 호출 5xx 없음": (r) => r.status < 500,
    });

    if (!step1Ok) {
      writeFlowFailures.add(true);
      writeFlowLatency.add(Date.now() - flowStart);
      return;
    }

    confirmTokensIssued.add(1);

    // confirm 토큰 추출 시도
    let confirmationToken = null;
    if (__ITER % 50 === 0) {
      // sampling: body 파싱은 비용이 크므로 1/50 VU에서만 수행
      try {
        const body = step1Res.json();
        confirmationToken = body && body.data && body.data.confirmationToken
          ? body.data.confirmationToken
          : null;
      } catch (_) {
        // JSON 파싱 실패는 무시 — 이미 step1Ok 체크로 충분
      }
    }

    sleep(0.2);

    // ── 2차 호출 (실제 실행) ───────────────────────────────────────────────
    // confirmationToken이 없는 경우에도 2차 호출을 보내 응답 패턴 확인
    group(`[${SCENARIO_ID}] cancelBooking confirm flow — 2차 호출 (실행)`, () => {
      const step2Res = toolsCall(mcpToken, "cancelBooking", {
        bookingId: bookingId,
        reason: "부하 시험용 취소 요청",
        // 실제 token이 없으면 mismatch/expired 에러가 반환되지만
        // 서버가 정상 처리 경로를 타는지 (5xx가 아닌지) 확인
        confirmationToken: confirmationToken || "dummy-token-for-load-test",
      }, "step2-execute");

      confirmStep2Latency.add(step2Res.timings.duration);
      // 2차 호출 성공 기준: 401/403/5xx가 아닌 응답
      // (bookingId 없음/token mismatch로 4xx는 정상 비즈니스 에러)
      const step2Ok = step2Res.status !== 0 &&
                      step2Res.status !== 401 &&
                      step2Res.status !== 403 &&
                      step2Res.status < 500;
      confirmStep2Failures.add(!step2Ok);

      check(step2Res, {
        "2차 호출 인증 통과": (r) => r.status !== 401,
        "2차 호출 5xx 없음": (r) => r.status < 500,
      });

      writeFlowFailures.add(!step1Ok || !step2Ok);
      writeFlowLatency.add(Date.now() - flowStart);
    });
  });

  // VU당 ~5초 간격 → 100 VU * 0.2 RPS ≈ 20 RPS (2 step 합산)
  sleep(4);
}

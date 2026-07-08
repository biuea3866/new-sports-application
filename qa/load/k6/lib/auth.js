// qa/load/k6/lib/auth.js
// 공통 인증·환경 검사 헬퍼.

import http from "k6/http";
import { check, fail } from "k6";

const API_URL = __ENV.QA_API_URL || "http://localhost:8080";

/**
 * 운영 환경 부하 차단.
 * QA_API_URL이 localhost 또는 *.local이 아니면 실행 중단.
 */
export function assertSafeTarget() {
  const isLocal = /^https?:\/\/(localhost|127\.0\.0\.1|.*\.local)(:|\/|$)/.test(API_URL);
  if (!isLocal) {
    fail(
      `[SAFETY] QA_API_URL=${API_URL}는 로컬 대상이 아닙니다. ` +
      `운영·staging 부하는 별도 승인이 필요합니다.`
    );
  }
}

/**
 * 인증 토큰 발급. setup() 단계에서 1회 호출하고 모든 VU가 공유.
 * 매 VU마다 발급하면 인증 서버에도 부하가 가서 측정 대상이 분리되지 않음.
 *
 * BE 계약: POST /auth/login, body { email, password } (AuthApiController + LoginRequest).
 * fixture 사용자 시드가 필요한 시나리오에서만 사용. /bookings·/events 처럼
 * X-User-Id 헤더 기반 엔드포인트는 headerAuth() 를 사용한다.
 */
export function issueToken(email = "qa@example.com", password = "qa-pass") {
  const res = http.post(
    `${API_URL}/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(res, { "token issued": (r) => r.status === 200 }) ||
    fail(`token issue failed: status=${res.status} body=${res.body}`);
  const token = res.json("accessToken");
  if (!token) fail(`token not in response: ${res.body}`);
  return token;
}

/**
 * 공통 헤더. setup에서 받은 토큰을 default 함수가 매 요청에 사용.
 */
export function authHeaders(token) {
  return {
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
  };
}

/**
 * X-User-Id 헤더 기반 권한 헤더.
 * /bookings·/events 등은 permitAll + X-User-Id 모델이라 Bearer 토큰이 불필요하다.
 * 부하 측정에서 인증 서버 노이즈를 제거하고 VU별 독립 사용자를 단순 표현한다.
 */
export function headerAuth(userId) {
  return {
    "X-User-Id": String(userId),
    "Content-Type": "application/json",
  };
}

export { API_URL };

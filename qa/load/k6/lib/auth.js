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
 */
export function issueToken(username = "qa-user", password = "qa-pass") {
  const res = http.post(
    `${API_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
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

export { API_URL };

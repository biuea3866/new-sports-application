// qa/load/k6/lib/auth.js
// 공통 인증·환경 검사 헬퍼.

import http from "k6/http";
import { check, fail } from "k6";
import crypto from "k6/crypto";
import encoding from "k6/encoding";

const API_URL = __ENV.QA_API_URL || "http://localhost:8080";
const JWT_SECRET = __ENV.QA_JWT_SECRET || "";

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
 * FIX-01에서 확인(SecurityConfig.kt#configureAuthorization) — `/limited-drops/**`을 포함한
 * 다수 엔드포인트는 AUTH-04(X-User-Id → JWT 전환)로 `authenticated()`가 되어 이 헤더로는
 * 더 이상 인증되지 않는다(JwtAuthenticationFilter만 SecurityContext를 채운다). JWT 인증이
 * 필요한 엔드포인트는 [jwtAuth]를 사용한다 — 아직 전환되지 않은(X-User-Id permitAll) 엔드포인트가
 * 남아있다면 이 함수를 계속 사용한다.
 */
export function headerAuth(userId) {
  return {
    "X-User-Id": String(userId),
    "Content-Type": "application/json",
  };
}

function base64UrlEncode(input) {
  return encoding.b64encode(input, "rawurl");
}

/**
 * JWT(HS256) 클라이언트 자체 서명 헤더 — `/limited-drops` 등 AUTH-04 이후 `authenticated()`로
 * 전환된 엔드포인트용(FIX-01 발견). `/auth/login`(BCrypt 검증) 왕복 없이 다수 synthetic 유저를
 * 저비용으로 흉내내기 위한 것으로, QA_JWT_SECRET에 백엔드 `app.jwt.secret`(JwtTokenProvider.kt)과
 * 동일한 값을 주입해야 서명이 유효하다. JwtAuthenticationFilter는 토큰 클레임만으로 principal을
 * 구성하고 `users` 테이블을 조회하지 않으므로(JwtAuthenticationFilter.kt), 실제 유저 row 없이도
 * 동작한다 — 단, 대상 엔드포인트가 리소스 소유자 검증(예: LimitedDrop 개설자)을 요구하면 그
 * 소유자 id와 일치하는 sub을 사용해야 한다(그렇지 않으면 Product.requireOwnedBy가
 * ResourceNotFoundException으로 오인 처리한다 — 소유권 없음을 404로 감춘다).
 */
export function jwtAuth(userId) {
  if (!JWT_SECRET) {
    fail(
      "QA_JWT_SECRET이 설정되지 않았습니다 — JWT 인증 엔드포인트(예: /limited-drops)는 " +
      "X-User-Id로 통과하지 않습니다(AUTH-04). 백엔드 app.jwt.secret과 동일한 값을 주입하세요."
    );
  }
  const nowSeconds = Math.floor(Date.now() / 1000);
  const header = { alg: "HS256", typ: "JWT" };
  const payload = {
    sub: String(userId),
    email: `synthetic-${userId}@loadtest.local`,
    roles: ["USER"],
    jti: `qa-${userId}-${nowSeconds}-${Math.random().toString(36).slice(2)}`,
    iat: nowSeconds,
    exp: nowSeconds + 1800,
  };
  const signingInput = `${base64UrlEncode(JSON.stringify(header))}.${base64UrlEncode(JSON.stringify(payload))}`;
  const signature = crypto.hmac("sha256", JWT_SECRET, signingInput, "base64rawurl");
  return {
    Authorization: `Bearer ${signingInput}.${signature}`,
    "Content-Type": "application/json",
  };
}

export { API_URL };

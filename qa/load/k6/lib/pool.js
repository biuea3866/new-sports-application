// qa/load/k6/lib/pool.js
// INFRA-01 FR-9: synthetic 신원 풀·partner 토큰 재사용.
//
// 근거: TDD "synthetic 격리 계약" — 유저는 X-User-Id 900000+ 범위(부하 전용),
// B2B는 provision 단계가 사전 발급한 partner API 키를 전 VU가 공유·재사용한다
// (요청마다 재발급하지 않음 — 인증 서버 노이즈 제거, lib/auth.js headerAuth() 관례 계승).

// synthetic 유저 ID 시작값 — 실사용자 ID·이메일(synthetic+<n>@loadtest.local)과 겹치지 않는 범위.
export const SYNTHETIC_USER_ID_BASE = 900000;

const DEFAULT_POOL_SIZE = 1000;

/**
 * VU 번호를 synthetic X-User-Id로 매핑한다. QA_USER_POOL_SIZE(env, 기본 1000)만큼
 * 순환시켜 동시 VU 수와 무관하게 유한한 synthetic 유저 집합을 재사용한다.
 *
 * @param {number} vu k6 __VU 값(1부터 시작)
 * @returns {number} 900000 이상의 synthetic X-User-Id
 */
export function syntheticUserId(vu) {
  const poolSize = Number(__ENV.QA_USER_POOL_SIZE) || DEFAULT_POOL_SIZE;
  return SYNTHETIC_USER_ID_BASE + (vu % poolSize);
}

/**
 * B2B partner 인증 헤더. PARTNER_API_KEY(env)에는 provision 단계(`POST /api/admin/partners`)가
 * 발급한 `partner_<keyId>_<random>` 형식 키가 이미 담겨 있으며, 모든 VU가 동일 값을 공유한다
 * (요청마다 재발급하지 않음 — no re-issue, FR-9).
 *
 * @returns {{Authorization: string, "Content-Type": string}}
 */
export function partnerAuthHeaders() {
  return {
    Authorization: `Bearer ${__ENV.PARTNER_API_KEY}`,
    "Content-Type": "application/json",
  };
}

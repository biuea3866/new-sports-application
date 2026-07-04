// INFRA-01 검증 — lib/pool.js (FR-9 유저 풀·토큰 재사용)
//
// pool.js는 k6 런타임 전역 __ENV를 참조한다. node 실행 환경에는 없으므로
// 테스트에서 globalThis.__ENV를 주입해 순수 로직만 검증한다.
import { test } from "node:test";
import assert from "node:assert/strict";

globalThis.__ENV = { QA_USER_POOL_SIZE: "1000", PARTNER_API_KEY: "partner_test-key_abc123" };

const { syntheticUserId, partnerAuthHeaders, SYNTHETIC_USER_ID_BASE } = await import("../pool.js");

test("syntheticUserId는 900000 이상 범위로 VU를 매핑한다", () => {
  assert.equal(syntheticUserId(1), SYNTHETIC_USER_ID_BASE + 1);
  assert.ok(syntheticUserId(1) >= 900000);
});

test("syntheticUserId는 QA_USER_POOL_SIZE로 순환한다", () => {
  globalThis.__ENV.QA_USER_POOL_SIZE = "10";
  assert.equal(syntheticUserId(15), SYNTHETIC_USER_ID_BASE + 5);
  globalThis.__ENV.QA_USER_POOL_SIZE = "1000";
});

test("partnerAuthHeaders는 env PARTNER_API_KEY를 Bearer 헤더로 재사용한다(재발급 없음)", () => {
  const first = partnerAuthHeaders();
  const second = partnerAuthHeaders();
  assert.equal(first.Authorization, "Bearer partner_test-key_abc123");
  assert.deepEqual(first, second);
});

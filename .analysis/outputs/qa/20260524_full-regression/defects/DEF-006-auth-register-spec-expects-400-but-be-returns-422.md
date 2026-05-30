# DEF-006 (spec) E2E-01-E01·E02 가입 검증 실패 시 spec이 400을 단언하지만 BE는 422 반환

## 메타
- layer: AMBIGUOUS
- severity: Minor
- auto-fix-eligible: false
- source-scenario: E2E-01-E01, E2E-01-E02
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:11-12` — `✘ 8 E2E-01-E01 이메일 형식 위반 가입 시 400`, `✘ 9 E2E-01-E02 비밀번호 길이 미달 가입 시 400`
- `logs/e2e-regression.log:100-105`:
  ```
  Expected: 400
  Received: 422
  > 126 |       expect(res.status()).toBe(400);
  ```
- BE는 RFC 9457 ValidationError로 422를 반환하는 것으로 보이며 이는 정상 동작 가능성이 있음. spec은 400만 단언. **layer 결정 불확실(BE 정책 vs spec 기대 미스매치)** — 사람 검토 후 BE 정책을 확정하고 spec을 `[400, 422]` 허용으로 수정하거나, 정책을 400으로 고정.

## 재현 단계
1. spec 실행: `npx playwright test specs/auth-register-login.spec.ts -g "E2E-01-E01"`
2. 단언 실패 결과 확인 — `Expected: 400, Received: 422`
3. E2E-01-E02 동일 패턴 (비밀번호 길이 미달)

## 기대 동작
spec: HTTP 400.
BE 정책(추정): RFC 9457 ValidationError → HTTP 422.

## 실제 동작
spec이 400만 단언 → BE의 422 응답을 실패로 분류. 두 케이스 모두 동일 원인.

## 영향 범위
- 영향 사용자: 없음 (회귀 테스트 분류 정확도 문제)
- 영향 화면/엔드포인트: `POST /users` (또는 회원가입 엔드포인트)
- 데이터 영향: 없음

## 아티팩트
- [trace E2E-01-E01](../artifacts/auth-register-login-E2E-01-8a4e5-E-01-E01-이메일-형식-위반-가입-시-400-chromium/trace.zip)
- [trace E2E-01-E02](../artifacts/auth-register-login-E2E-01-15f52--01-E02-비밀번호-길이-미달-가입-시-400-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 11, 12, 96~146)

## 의심 코드 경로
- `qa/e2e/specs/auth-register-login.spec.ts:126`·`:136` — 단언 `expect(res.status()).toBe(400)` → `[400, 422].toContain(res.status())` 또는 422로 변경 검토
- BE 정책 확정 시 변경 위치는 spec 또는 BE — 사람 결정 사항

## 자동 수정 지시
해당 없음 (`auto-fix-eligible: false`). 사람이 BE 정책(400 vs 422)을 확정한 뒤 spec 또는 BE 한쪽을 수정.

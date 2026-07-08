# DEF-009 (spec) E2E-08-E04 빈 메시지 POST 시 `.toContain()` 단언 인자 반전 오류

## 메타
- layer: AMBIGUOUS
- severity: Minor
- auto-fix-eligible: false
- source-scenario: E2E-08-E04
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:60` — `✘ 57 E2E-08-E04 빈 메시지 내용 POST 시 400 (54ms)`
- `logs/e2e-regression.log:254-262`:
  ```
  Error: expect(received).toContain(expected) // indexOf
  Expected value: 500
  Received array: [400, 422]
  > 185 |     expect([400, 422]).toContain(res.status());
  ```
- e2e-report.md:51-53 — 실제 BE 응답은 **403**(방 미참여자) — 정상 권한 검증 응답. spec assertion `[400, 422].toContain(res.status())`는 res.status가 500이면 "Expected: 500, Received array: [400, 422]" 에러를 출력하지만 실제 res.status는 403으로 추정 — 어쨌든 spec이 403을 허용 목록에 두지 않아 fail. **layer: AMBIGUOUS** (spec 허용 목록 누락) / 사람 검토 필요.

## 재현 단계
1. spec 실행: `npx playwright test specs/notification-message.spec.ts -g "E2E-08-E04"`
2. 단언 결과 확인 — 출력은 "Expected value: 500" 형태로 인자 반전된 메시지 표시
3. 실제 응답은 spec 사전 조건(`방 미참여자` user-fixture)으로 인해 403 반환

## 기대 동작
spec 의도: 빈 메시지 내용일 때 400 또는 422.
실제 시드/권한 조건: 방 미참여자이므로 권한 검증이 먼저 동작해 403 반환.

## 실제 동작
spec이 `[400, 422]`만 허용 → 403도 500도 매칭 실패. BE는 정상 동작(403 권한 응답).

## 영향 범위
- 영향 사용자: 없음 (회귀 테스트 분류 정확도)
- 영향 화면/엔드포인트: `POST /rooms/{roomId}/messages`
- 데이터 영향: 없음

## 아티팩트
- [trace](../artifacts/notification-message-E2E-0-6b5e4--08-E04-빈-메시지-내용-POST-시-400-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 60, 252~276)

## 의심 코드 경로
- `qa/e2e/specs/notification-message.spec.ts:185` — 단언을 `[400, 422, 403]` 허용 또는 시드를 방 참여자로 변경하여 의도된 검증 분기(400/422)에 도달하도록 수정

## 자동 수정 지시
해당 없음 (`auto-fix-eligible: false`). 사람이 spec의 검증 의도(빈 메시지 검증 vs 권한 검증)를 확정하고 시드 또는 단언을 수정.

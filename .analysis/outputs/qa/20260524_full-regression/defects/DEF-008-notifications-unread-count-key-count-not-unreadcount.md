# DEF-008 (spec) E2E-08-03·E2E-08-E03 unread-count 응답 키가 `count`인데 spec은 `unreadCount` 기대

## 메타
- layer: AMBIGUOUS
- severity: Minor
- auto-fix-eligible: false
- source-scenario: E2E-08-03, E2E-08-E03
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:51` — `✘ 48 E2E-08-03 GET /notifications/me/unread-count — 200 + unreadCount 숫자`
- `logs/e2e-regression.log:59` — `✘ 56 E2E-08-E03 알림 0건 user — unread-count 0`
- `logs/e2e-regression.log:202-210`:
  ```
  Expected: "number"
  Received: "undefined"
  > 54 |     expect(typeof body.unreadCount).toBe("number");
  ```
- e2e-report.md:49-50 — BE 실제 응답 키는 `body.count`. spec은 `body.unreadCount` 참조. 200 응답 자체는 정상. **layer 결정 불확실(BE 응답 키 vs spec 기대)** — 사람이 표준 키 결정 후 한쪽 수정.

## 재현 단계
1. 로그인된 user로 `GET /notifications/me/unread-count` 호출
2. 응답 body 구조 확인 — `body.count`는 숫자, `body.unreadCount`는 undefined
3. spec 단언 실패

## 기대 동작
spec: `typeof body.unreadCount === "number"` & `body.unreadCount === 0`.
BE 실제: `body.count`가 숫자.

## 실제 동작
spec이 `body.unreadCount` 참조 → undefined로 실패. BE 응답은 정상이나 키 이름이 다름.

## 영향 범위
- 영향 사용자: 없음 (회귀 테스트 분류 정확도)
- 영향 화면/엔드포인트: `GET /notifications/me/unread-count`
- 데이터 영향: 없음

## 아티팩트
- [trace E2E-08-03](../artifacts/notification-message-E2E-0-35e07--count-—-200-unreadCount-숫자-chromium/trace.zip)
- [trace E2E-08-E03](../artifacts/notification-message-E2E-0-3fb75-알림-0건-user-—-unread-count-0-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 51, 59, 200~250)

## 의심 코드 경로
- `qa/e2e/specs/notification-message.spec.ts:54`·`:174` — `body.unreadCount` → `body.count`로 변경 검토
- BE 응답 모델 — 응답 키 컨벤션(`count` vs `unreadCount`) 확정 필요

## 자동 수정 지시
해당 없음 (`auto-fix-eligible: false`). 사람이 응답 키 컨벤션을 확정한 뒤 spec 또는 BE 한쪽을 수정.

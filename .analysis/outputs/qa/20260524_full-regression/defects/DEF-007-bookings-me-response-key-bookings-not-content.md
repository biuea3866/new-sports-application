# DEF-007 (spec) E2E-03-03 GET /bookings/me 응답 키 이름이 spec 기대(`content`/`items`)와 다름

## 메타
- layer: AMBIGUOUS
- severity: Minor
- auto-fix-eligible: false
- source-scenario: E2E-03-03
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:16` — `✘ 13 E2E-03-03 GET /bookings/me — 200 + Page 응답 구조 (411ms)`
- `logs/e2e-regression.log:150-158`:
  ```
  Expected: true
  Received: false
  > 49 |     expect(Array.isArray(body.content ?? body.items)).toBe(true);
  ```
- e2e-report.md:48 — BE 실제 응답 키는 `body.bookings`. spec은 `content`/`items`만 확인. 200 응답·페이지 구조 정상. **layer 결정 불확실(BE 응답 키 컨벤션 vs spec 기대)** — Page 응답 키 표준 확정 필요.

## 재현 단계
1. 로그인된 user로 `GET /bookings/me` 호출
2. 응답 body 구조 확인 — `body.bookings` 배열이 존재하지만 `body.content`/`body.items`는 없음
3. spec 단언 실패

## 기대 동작
spec: `Array.isArray(body.content ?? body.items) === true`.
BE 실제: `body.bookings`가 배열로 존재.

## 실제 동작
spec 단언이 false. BE 응답은 200 + Page 구조이지만 키 이름이 `bookings`.

## 영향 범위
- 영향 사용자: 없음 (회귀 테스트 분류 정확도)
- 영향 화면/엔드포인트: `GET /bookings/me`
- 데이터 영향: 없음

## 아티팩트
- [trace](../artifacts/booking-create-list-E2E-03-b7439-ookings-me-—-200-Page-응답-구조-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 16, 148~170)

## 의심 코드 경로
- `qa/e2e/specs/booking-create-list.spec.ts:49` — `body.content ?? body.items` → `body.bookings ?? body.content ?? body.items` 또는 표준 키로 통일
- BE 응답 응답 모델 — 도메인별 컬렉션 키(`bookings`) 사용 컨벤션 vs Spring Page 표준(`content`) 확정 필요

## 자동 수정 지시
해당 없음 (`auto-fix-eligible: false`). 사람이 Page 응답 키 컨벤션을 확정한 뒤 spec 또는 BE 한쪽을 수정.

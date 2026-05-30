# DEF-005 POST /events/{id}/seats/select 빈 seatIds 입력 시 500 (400/422 기대)

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: E2E-05-E05
- detected-at: 2026-05-24T18:33:14+09:00
- environment: docker-compose.qa.yml (BE commit 74e82bf)
- related-pr: none
- related-ticket: none

## 분류 근거
- `logs/e2e-regression.log:93` — `✘ 90 ... E2E-05-E05 빈 seatIds 로 select 호출 시 400 (23ms)`
- `logs/e2e-regression.log:358-366`:
  ```
  Error: expect(received).toContain(expected) // indexOf
  Expected value: 500
  Received array: [400, 422]
  > 218 |       expect([400, 422]).toContain(res.status());
  ```
- spec은 400/422 허용, 실제 응답 500 — 빈 배열 입력에 대한 검증 미처리. spec 측 `toContain` 인자 배치는 정상 (배열이 호출자, 값이 인자). **layer: BE** / severity 상속(Major).

## 재현 단계
1. 로그인된 user 토큰 확보
2. `POST /events/{id}/seats/select`에 body `{"seatIds": []}` 전송
3. 응답 상태 코드 확인

## 기대 동작
시나리오 md(`qa/e2e/scenarios/ticket-event-purchase.md:41`) — 빈 seatIds `[]`로 호출 시 400 Bad Request 반환.

## 실제 동작
HTTP 500 Internal Error. 빈 배열에 대한 사전 검증(`@NotEmpty` 또는 명시적 가드) 누락으로 추정.

## 영향 범위
- 영향 사용자: 좌석 선택 클라이언트
- 영향 화면/엔드포인트: `POST /events/{id}/seats/select`
- 데이터 영향: 없음

## 아티팩트
- [trace](../artifacts/ticket-event-purchase-E2E--17838-빈-seatIds-로-select-호출-시-400-chromium/trace.zip)
- [regression log](../logs/e2e-regression.log) (line 93, 356~380)

## 의심 코드 경로
- `backend/src/main/kotlin/com/sportsapp/presentation/ticketing/EventApiController.kt` — seatIds 검증 누락
- `SeatSelectRequest` DTO — `@NotEmpty` / `@Size(min=1)` 미부착 가능성

## 자동 수정 지시
대상 에이전트: be-implementer
작업 범위:
- 결함 한정 — 빈 seatIds 입력에 대한 검증만 추가. 좌석 락 로직·트랜잭션은 손대지 않음.
- TDD 사이클: 빈 배열 입력 시 400 응답을 단언하는 시나리오 테스트 먼저 RED.
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/.../EventApiControllerTest.kt`.
- 예상 변경 파일 수: 1~2개 (Request DTO 또는 Controller validation + 테스트).

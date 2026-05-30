# DEF-007 빈 seatIds 로 POST /events/{id}/seats/select 호출 시 500 — Bean Validation 부재

## 메타
- layer: BE
- severity: Major
- auto-fix-eligible: true
- source-scenario: E2E-05-E05
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- E2E error-context (`ticket-event-purchase-E2E--17838-*-chromium/error-context.md`):
  > `Expected value: 500` / `Received array: [400, 422]` — playwright `toContain` 출력 순서 혼동. 즉 응답 status=500, spec은 [400, 422] 기대
- `EventApiController.kt:61-69`:
  ```kotlin
  @PostMapping("/{id}/seats/select")
  fun selectSeats(
      ...
      @RequestBody request: SelectSeatsRequest,  // ← @Valid 부재
  ): ...
  ```
  `@Valid` 미적용 + `SelectSeatsRequest.seatIds`에 `@NotEmpty`/`@Size(min=1)` 미적용 추정 → 빈 리스트가 도메인까지 진입해 NPE/IndexOutOfBounds 등 → 500
- BE 입력 검증 부재 결함 → layer: BE
- 빈 입력으로 실행 부담은 낮으나 5xx 노출은 부적절 → Major

## 재현 단계
1. BE 기동
2. `POST /events/1/seats/select` 호출:
   - `X-User-Id: 1`, `Content-Type: application/json`
   - body: `{ "seatIds": [] }`
3. **500 응답** (400 또는 422 기대)

## 기대 동작
HTTP 400 또는 422 + ProblemDetail (validation 에러 본문). 시나리오 md `qa/e2e/scenarios/ticket-event-purchase.md`의 Then: "빈 입력은 검증 단계에서 거부".

## 실제 동작
HTTP 500 + `INTERNAL_ERROR` ProblemDetail.

## 영향 범위
- 영향 사용자: 발권 페이지에서 좌석 선택 도중 0개 선택으로 제출하는 사용자 (FE 클라이언트 버그가 비어 보낼 가능성, API 직접 호출 사용자)
- 영향 화면/엔드포인트: `POST /events/{id}/seats/select`, `POST /events/{id}/seats/release` (같은 `SelectSeatsRequest` 사용 — 컨트롤러 L75 — 동일 결함 가능)
- 데이터 영향: 없음 (read+lock 시도)

## 아티팩트
- [E2E-05-E05 error-context](../artifacts/playwright-output/ticket-event-purchase-E2E--17838-빈-seatIds-로-select-호출-시-400-chromium/error-context.md)

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/presentation/ticketing/EventApiController.kt` | 61-80 | `@RequestBody request: SelectSeatsRequest` 에 `@Valid` 미부착. selectSeats/releaseSeats 둘 다 |
| `backend/src/main/kotlin/com/sportsapp/presentation/ticketing/SelectSeatsRequest.kt` 또는 동등 위치 | (확인 필요) | `seatIds: List<Long>` 에 `@NotEmpty` / `@Size(min=1)` 부재 추정 |
| `backend/src/main/kotlin/com/sportsapp/application/ticketing/SelectSeatsUseCase.kt` 또는 DomainService | (확인 필요) | 빈 리스트 진입 시 NPE/IOOBE 가능 — Rich Domain 측 require 강제 자리 |

가설:
- A) `SelectSeatsRequest`의 `seatIds`에 `@field:NotEmpty(message = "seatIds must not be empty")` + 컨트롤러에 `@Valid` 추가. 가장 단순. DEF-003 fix 후엔 400 매핑이 옳음
- B) 도메인 측 `SelectSeatsCommand` 또는 Entity에서 `require(seatIds.isNotEmpty())` 추가 — Rich Domain 캡슐화 (be-code-convention.md "Self-Validation 캡슐화" 참조)

## 자동 수정 지시
대상 에이전트: be-implementer

작업 범위:
- 결함 한정 — `seatIds` 빈 리스트 검증만. 다른 요청 DTO·Use Case 리뷰 금지 (CLAUDE.md §3 정밀한 수정)
- TDD 사이클:
  1. **RED**: `EventApiControllerTest.kt`에 `seatIds: []`로 호출 시 400/422 응답을 검증하는 케이스 추가. selectSeats + releaseSeats 둘 다 (releaseSeats도 같은 DTO 사용)
  2. **GREEN**: `SelectSeatsRequest`에 `@field:NotEmpty` + 컨트롤러에 `@Valid` 부착. 또는 도메인 측 `require` 추가
  3. **GREEN 검증**: E2E-05-E05 통과
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/presentation/ticketing/EventApiControllerTest.kt`
- 예상 변경 파일 수: 2~3개 (DTO + 컨트롤러 + 테스트)
- **반드시 점검**: DEF-003 (validation 422 → 400 매핑) fix가 함께 적용돼야 본 결함이 400 응답으로 일치. 두 결함 fix 순서 — DEF-003 먼저 또는 동시 처리 권장

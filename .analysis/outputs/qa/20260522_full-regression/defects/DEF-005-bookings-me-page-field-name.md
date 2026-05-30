# DEF-005 GET /bookings/me 응답 페이지 구조 — content/items 필드 부재 (bookings 로 직렬화)

## 메타
- layer: BE
- severity: Critical
- auto-fix-eligible: true
- source-scenario: E2E-03-03
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- `booking-create-list-E2E-03-b7439-*-chromium/error-context.md`:
  > spec L48-49: `expect(body).toHaveProperty("totalElements"); expect(Array.isArray(body.content ?? body.items)).toBe(true);`
  > 실패 단언: `expect(Array.isArray(body.content ?? body.items)).toBe(true)` — `Received: false`
  → `totalElements` 는 존재하나 `content` / `items` 배열 필드 부재 → 응답 직렬화 형식 결함
- `backend/src/main/kotlin/com/sportsapp/application/booking/ListBookingsResponse.kt:5-11`:
  ```kotlin
  data class ListBookingsResponse(
      val bookings: List<BookingResponse>,
      val totalElements: Long,
      ...
  )
  ```
  → 페이지 배열 필드명이 `bookings`. Spring Data `Page` 표준(`content`)과 불일치
- BE 응답 형식 결함 → layer: BE
- 마이페이지 예약 목록은 사용자 핵심 플로우 → Critical

## 재현 단계
1. BE 기동
2. `GET /bookings/me` 호출 (`X-User-Id: 1` 헤더)
3. 200 응답 + body 확인:
   ```json
   { "bookings": [], "totalElements": 0, ... }
   ```
4. **`content` / `items` 필드 부재** — Spring Data Page 표준과 불일치

## 기대 동작
Spring Data `Page<T>` 표준 직렬화 또는 `content` 필드 사용:
```json
{ "content": [...], "totalElements": 0, "totalPages": 0, "size": 20, "number": 0 }
```

다른 list 엔드포인트(`GET /payments/me`)는 `Page<PaymentResponse>` 직접 반환 → `content` 필드 정상 → 본 컨트롤러만 별도 응답 DTO 사용 중

## 실제 동작
```json
{
  "bookings": [],
  "totalElements": 0,
  "totalPages": 0,
  "page": 0,
  "size": 20
}
```

## 영향 범위
- 영향 사용자: FE 클라이언트 (web/mobile) — `body.content`를 기대하는 코드가 깨짐. 모바일 앱이 `bookings` 필드를 명시적으로 사용 중이라면 영향 없음. FE 코드 확인 필요
- 영향 화면/엔드포인트: `GET /bookings/me` 단독. 다른 list 응답(`/payments/me`, `/events`, `/notifications/me`)은 `Page<T>` 직접 반환 → 영향 없음
- 데이터 영향: 없음

## 아티팩트
- [E2E-03-03 error-context](../artifacts/playwright-output/booking-create-list-E2E-03-b7439-ookings-me-—-200-Page-응답-구조-chromium/error-context.md)

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/application/booking/ListBookingsResponse.kt` | 5-21 | 배열 필드명 `bookings` — `content`로 변경 또는 `Page<BookingResponse>` 직접 반환으로 전환 |
| `backend/src/main/kotlin/com/sportsapp/presentation/booking/BookingApiController.kt` | 38-51 | 응답 타입 `ResponseEntity<ListBookingsResponse>` — `Page<BookingResponse>` 로 전환 시 컨트롤러도 함께 |
| `backend/src/main/kotlin/com/sportsapp/application/booking/ListMyBookingsUseCase.kt` | (확인 필요) | UseCase가 Page를 ListBookingsResponse로 매핑하는 단계 |

가설:
- A) `ListBookingsResponse`의 `bookings` 필드명을 `content`로 변경 (가장 단순)
- B) `Page<BookingResponse>` 를 직접 반환하도록 컨트롤러·UseCase 수정 (다른 list 엔드포인트와 일관)
- C) FE가 `bookings` 필드를 기대하고 있다면 FE 측 수정도 함께 필요 — be-implementer가 FE 호출부 grep으로 확인:
  ```bash
  grep -rn "\.bookings" web/lib web/app mobile/lib mobile/screens --include="*.ts" --include="*.tsx"
  ```

## 자동 수정 지시
대상 에이전트: be-implementer

작업 범위:
- 결함 한정 — `ListBookingsResponse` + 컨트롤러 매핑 한 곳만 수정. 다른 list 엔드포인트 일관성 정리 금지 (별도 티켓 — CLAUDE.md §3 정밀한 수정)
- TDD 사이클:
  1. **RED**: `BookingApiControllerTest.kt`에 `GET /bookings/me` 응답 JSON에 `content` 배열 + `totalElements` 가 존재함을 검증하는 케이스 추가
  2. **GREEN**: `ListBookingsResponse` 필드명 `bookings` → `content` 변경. 또는 `Page<BookingResponse>` 직접 반환으로 전환
  3. **GREEN 검증**: E2E-03-03 통과 + 기존 BE 통합 테스트 회귀 없음
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/presentation/booking/BookingApiControllerTest.kt`
- 예상 변경 파일 수: 2~3개 (DTO + 컨트롤러 + 테스트). FE 호출부가 있으면 +1
- **반드시 점검**: FE/모바일에서 `bookings` 필드를 직접 참조하는지:
  ```bash
  grep -rn '"bookings":\|\.bookings\[' web/lib web/app mobile --include="*.ts" --include="*.tsx" --include="*.kt"
  ```

# DEF-003 Bean Validation 실패가 422 로 매핑됨 — 회원가입 입력 검증에서 400 기대치 불일치

## 메타
- layer: BE
- severity: Critical
- auto-fix-eligible: true
- source-scenario: E2E-01-E01, E2E-01-E02 (2건 — 같은 ExceptionHandler 경로)
- detected-at: 2026-05-22T02:20:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- `auth-register-login-E2E-01-*-chromium/error-context.md`:
  > `Expected: 400` / `Received: 422`
- `backend/src/main/kotlin/com/sportsapp/presentation/exception/GlobalExceptionHandler.kt:41-56`:
  > `MethodArgumentNotValidException` → `ErrorStatus.UNPROCESSABLE` (httpStatus 422)
- BE가 의도적으로 422를 반환하고 있어 200/4xx 범주의 BE 응답 — 즉 **응답 매핑 결함**이지 인프라/FE 결함이 아님 → layer: BE
- 회원가입은 핵심 비즈니스 플로우 → severity: Critical

## 재현 단계
1. BE 기동 (`./gradlew bootRun`)
2. 잘못된 이메일 형식으로 회원가입 요청:
   ```
   POST /users/register
   { "email": "not-an-email", "password": "Passw0rd!" }
   ```
3. **422 응답 — 400 기대**
4. 짧은 비밀번호로 재시도:
   ```
   POST /users/register
   { "email": "<unique>@example.com", "password": "abc" }
   ```
5. **422 응답 — 400 기대**

## 기대 동작
입력 형식 위반은 HTTP 400 (Bad Request) 으로 매핑 — 클라이언트 측 요청 결함의 표준 신호.
시나리오 md `qa/e2e/scenarios/auth-register-login.md`의 Then: "400 + 검증 에러 본문".

## 실제 동작
```
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{
  "status": 422,
  "title": "...",
  "detail": "Request validation failed",
  "code": "VALIDATION_ERROR",
  "fieldErrors": [...]
}
```

## 영향 범위
- 영향 사용자: 입력 검증 실패한 모든 클라이언트 (FE 회원가입 폼, 외부 API 통합)
- 영향 화면/엔드포인트: `@Valid @RequestBody`를 사용하는 모든 컨트롤러 — 회원가입·결제 생성·로그인 등 광범위
- 데이터 영향: 없음 (요청이 거부됨)

## 아티팩트
- [E2E-01-E01 error-context](../artifacts/playwright-output/auth-register-login-E2E-01-8a4e5-E-01-E01-이메일-형식-위반-가입-시-400-chromium/error-context.md)
- [E2E-01-E02 error-context](../artifacts/playwright-output/auth-register-login-E2E-01-15f52--01-E02-비밀번호-길이-미달-가입-시-400-chromium/error-context.md)

## 의심 코드 경로

| 파일 | 라인 | 역할 |
|---|---|---|
| `backend/src/main/kotlin/com/sportsapp/presentation/exception/GlobalExceptionHandler.kt` | 41-56 | `MethodArgumentNotValidException` 핸들러가 `ErrorStatus.UNPROCESSABLE` (422) 반환 — 표준은 400 |
| `backend/src/main/kotlin/com/sportsapp/domain/common/BusinessException.kt` | 11-19 | `ErrorStatus` enum — 422/400 모두 존재. 어느 status로 매핑할지 결정 |

가설:
- A) 단순히 `ErrorStatus.UNPROCESSABLE` → `ErrorStatus.BAD_REQUEST`로 변경 — 가장 가능성 높은 fix
- B) 또는 422 매핑을 유지하고 E2E spec을 422로 갱신 — 단 회원가입 시나리오는 "입력 형식 위반"이라 400이 RFC 7231 의도에 부합. BE 매핑 수정이 적절

## 자동 수정 지시
대상 에이전트: be-implementer

작업 범위:
- 결함 한정 — `GlobalExceptionHandler.kt` 의 `handleValidationException` 한 메서드 수정. 다른 핸들러 / `ErrorStatus` enum 정의 변경 금지 (CLAUDE.md §3 정밀한 수정)
- TDD 사이클:
  1. **RED**: `backend/src/test/kotlin/com/sportsapp/presentation/exception/GlobalExceptionHandlerIntegrationTest.kt`에 잘못된 입력 → 400 응답을 검증하는 테스트 추가 (현재 422 기대로 작성돼 있다면 400으로 갱신)
  2. **GREEN**: `MethodArgumentNotValidException` 핸들러의 status를 `ErrorStatus.BAD_REQUEST`로 변경
  3. **GREEN 검증**: 동일 통합 테스트 + Playwright `auth-register-login.spec.ts` E2E-01-E01·E02 통과
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/presentation/exception/GlobalExceptionHandlerIntegrationTest.kt`
- 예상 변경 파일 수: 2개 (handler + test)

# E2E-01 회원가입 · 로그인 · 토큰 갱신 · 로그아웃

## 메타
- severity: Critical
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/auth/AuthApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/user/UserApiController.kt
  - web/app/page.tsx
  - mobile/app/(auth)/login.tsx
  - mobile/app/(auth)/register.tsx
- related-ticket: none
- estimated-duration: 1m

## 사전 조건
- DB 시드: `qa/e2e/fixtures/users-empty.sql` (가입 충돌 없는 깨끗한 user 테이블)
- 인증 상태: anonymous
- 환경 변수: `JWT_ACCESS_TOKEN_TTL=300s`, `JWT_REFRESH_TOKEN_TTL=86400s` (예시 기준)

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-01-01 | 미가입 이메일 `qa+new@test.local` | `POST /users/register`에 정상 페이로드를 보낼 때 | 201 Created와 `Location: /users/{id}` 헤더가 응답된다 |
| E2E-01-02 | E2E-01-01에서 가입한 계정 | `POST /auth/login`에 같은 자격 증명을 보낼 때 | 200 OK와 accessToken·refreshToken이 발급된다 |
| E2E-01-03 | E2E-01-02의 refreshToken | `POST /auth/refresh`를 호출할 때 | 새 accessToken이 발급되고 기존 토큰은 즉시 사용 불가 상태로 회전된다 |
| E2E-01-04 | E2E-01-02의 accessToken | `POST /auth/logout`을 `Authorization: Bearer <token>` 헤더로 호출할 때 | 204 No Content가 응답되고 동일 토큰의 후속 호출은 401을 받는다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-01-R01 | 이미 가입된 이메일로 재가입 요청 시 409 또는 도메인 예외 응답이 반환된다 |
| E2E-01-R02 | 잘못된 비밀번호로 로그인 시 401이 반환되고 어떤 토큰도 발급되지 않는다 |
| E2E-01-R03 | 만료된 refreshToken으로 갱신 호출 시 401과 함께 재로그인을 유도하는 코드가 반환된다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-01-E01 | 이메일 형식 위반(`not-an-email`)으로 가입 요청 시 400 Bad Request가 반환된다 |
| E2E-01-E02 | 비밀번호 길이 미달로 가입 요청 시 400 Bad Request가 반환된다 |
| E2E-01-E03 | logout 요청에 `Authorization` 헤더가 없으면 401이 반환된다 |

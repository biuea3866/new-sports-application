# DEF-001 BE 전역 401 — permitAll 엔드포인트가 미인증 요청에 401 반환

## 메타
- layer: BE
- severity: Critical
- auto-fix-eligible: true
- source-scenario: E2E-01-01, E2E-01-03, E2E-01-04, E2E-01-E01, E2E-01-E02, E2E-02-01, E2E-02-02, E2E-02-03, E2E-03-03, E2E-03-04, E2E-03-R01, E2E-04-04, E2E-04-05, E2E-04-R01, E2E-04-R02, E2E-04-E01, E2E-05-01, E2E-05-R01, E2E-08-R02, E2E-08-E03 / LOAD-01 (RC-3 2차 영향 포함: E2E-01-R01, E2E-01-R02, E2E-03-01, E2E-03-02, E2E-08-E04 외 다수)
- detected-at: 2026-05-23T09:03:40+00:00
- environment: docker-compose.qa.yml + BE next start production 빌드
- related-pr: none (메인 워크트리 미커밋 변경 포함)
- related-ticket: none

## 분류 근거
- `e2e-report.md` RC-1 — `POST /users/register`, `GET /facilities`, `GET /events`, `GET /payments/me` 등 다수 엔드포인트가 401 반환.
- `load-results/facility-search/threshold.txt:16-17` — `GET /facilities` 전 요청 401, response body `{"error":"unauthorized","message":"missing or invalid bearer token"}` 1,459건 수신.
- `backend/src/main/kotlin/com/sportsapp/infrastructure/security/SecurityConfig.kt:48,57-62` — `/auth/login`, `/auth/refresh`, `/users/register`, `/facilities/**`, `/events/**`, `/payments/**`, `/bookings/**`, `/notifications/**`, `/cart/**`, `/ticket-orders/**`, `/goods-orders/**` 가 명시적으로 `permitAll()` 로 설정됨.
- `backend/src/main/kotlin/com/sportsapp/infrastructure/security/JwtAuthenticationFilter.kt` — 토큰 없는 경우 SecurityContext 미설정만 하고 chain 통과(401 직접 반환하지 않음). 따라서 401은 다른 경로에서 발생.
- E2E-01-R03(만료 refreshToken 401) · E2E-01-E03(헤더 없이 logout 401) 은 통과 — 401 응답 자체는 의도된 경로에서는 정상.
- 두 신호 종합 → 의도된 `permitAll()` 범위와 실제 응답 코드가 불일치 → **layer: BE**.
- severity: 회원가입·로그인·결제·예약 등 핵심 비즈니스 플로우 다수가 막혀 시나리오 절반 이상이 실패. → Critical.

## 재현 단계
1. `docker-compose -f qa/e2e/docker-compose.qa.yml up -d` 인프라 기동
2. backend 모듈 production 기동 (`./gradlew :backend:bootRun --args='--spring.profiles.active=qa'`)
3. 인증 없이 신규 가입 호출:
   ```bash
   curl -sS -X POST http://localhost:8080/users/register \
     -H "Content-Type: application/json" \
     -d '{"email":"def001@test.local","password":"Passw0rd!"}'
   ```
4. 동일 BE 인스턴스로 `curl -sS http://localhost:8080/facilities?page=0&size=50` 호출

## 기대 동작
- `POST /users/register` → 201 Created + `Location: /users/{id}` 헤더
- `GET /facilities` → 200 + Page 응답 (시드 없으면 빈 content 배열)
- SecurityConfig 의 `permitAll()` 선언이 실제 응답에 반영됨

## 실제 동작
- `POST /users/register` → 401 Unauthorized
  - 응답 body: `{"status":401,"title":"Unauthorized","detail":"Authentication required"}`
- `GET /facilities` → 401 (load 1,459건 모두)
  - 응답 body: `{"error":"unauthorized","message":"missing or invalid bearer token"}`
- 응답 body 형식이 두 가지(`status/title/detail` vs `error/message`)로 갈리는 점에서 401 출처가 SecurityConfig 의 `jsonAuthenticationEntryPoint` 외에도 존재할 가능성

## 영향 범위
- 영향 사용자: 모든 미인증 진입(가입·로그인·시설 검색)·X-User-Id 기반 임시 인증 흐름 전체
- 영향 화면/엔드포인트: `/users/register`, `/auth/refresh`, `/auth/logout`, `/facilities/**`, `/events/**`, `/payments/**`, `/bookings/**`, `/notifications/**`, `/cart/**`, `/ticket-orders/**`, `/goods-orders/**`
- 데이터 영향: 없음 (401 즉시 반환으로 도메인 로직 실행 안 됨)
- 직접 실패 E2E: 20건 (RC-1)
- 2차 전파 실패 E2E: 33건 (RC-3 — `expect([200, 403, 404]).toContain(status)` 형태의 spec 에서 401 수신)
- 부하 시나리오: LOAD-01 facility-search 전부 401 (1,459/1,459건)

## 아티팩트
- [e2e-report](../e2e-report.md) — RC-1 표 및 RC-3 영향
- [e2e-run.log](../e2e-run.log) — 401 발생 라인 다수
- [facility-search threshold.txt](../load-results/facility-search/threshold.txt)
- [facility-search raw.log](../load-results/facility-search/raw.log)
- [E2E-01-01 trace](../artifacts/auth-register-login-E2E-01-700f7-1-Created-Location-헤더가-응답된다-chromium/trace.zip)
- [E2E-01-01 error-context](../artifacts/auth-register-login-E2E-01-700f7-1-Created-Location-헤더가-응답된다-chromium/error-context.md)
- [E2E-02-01 trace](../artifacts/facility-search-list-E2E-0-5a5fd-size-50-호출-시-200-Page-응답-구조-chromium/trace.zip)

## 의심 코드 경로
- `backend/src/main/kotlin/com/sportsapp/infrastructure/security/SecurityConfig.kt:48,57-62` — `permitAll()` 선언이 실제 적용되지 않음. 다른 `SecurityFilterChain` 빈이 우선순위로 덮어쓰거나, 컨텍스트 패스 prefix(`/api` 등) 누락 가능성. 응답 body 두 종(`status/title/detail` vs `error/message`)이 다른 점에서 두 번째 출처 의심.
- `backend/src/main/kotlin/com/sportsapp/infrastructure/security/JwtAuthenticationFilter.kt` — 토큰 없는 경로에서는 401 직접 발생시키지 않음. 검토 필요한 부수 영향은 없을 것으로 추정.
- `backend/src/main/resources/application.yml` — `server.servlet.context-path` 설정, Spring Security path matcher 매칭 모드(MVC vs Servlet) 검토 필요.

## 자동 수정 지시 (auto-fix-eligible=true)
대상 에이전트: be-implementer

작업 범위:
- 결함 한정: SecurityConfig 의 permitAll 매칭이 실제 응답으로 이어지도록 수정. 인접 인증/인가 로직 리팩토링 금지 (CLAUDE.md §3 정밀한 수정).
- 응답 body 형식 두 종이 동시에 존재하는 이유를 식별 — 추가 SecurityFilterChain 빈 또는 Spring Cloud Gateway/외부 필터 존재 여부 점검.
- TDD 사이클:
  1. RED: `POST /users/register` 익명 호출 시 201, `GET /facilities` 익명 호출 시 200 을 기대하는 통합 테스트(`@SpringBootTest` + MockMvc) 추가 — 현재 401 수신으로 실패.
  2. fix: SecurityConfig 또는 필터 체인 수정.
  3. GREEN: 위 테스트 통과 + 기존 인증 필요 엔드포인트(`/api/facility-owner/**`, `/admin/**`) 401/403 동작 회귀 통과.
- 테스트 위치 제안: `backend/src/test/kotlin/com/sportsapp/infrastructure/security/SecurityConfigIntegrationTest.kt`
- 예상 변경 파일 수: 1~2 (SecurityConfig + 신규 테스트 1). 3개 초과 시 사람 검토 권장.

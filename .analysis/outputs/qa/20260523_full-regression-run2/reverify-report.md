# 재검증 리포트 — 20260523_full-regression-run2

## 결과
**재검증 미실행 — 환경 부재 + fix 진단 미확정**

## 사유
1. **Docker daemon 종료** — Step 5-B(fix 통합 브랜치 회귀 재실행)에 필요한 docker-compose 인프라(MySQL/MongoDB/Redis/Kafka/Zookeeper)가 daemon 종료로 불가용. macOS `Docker Desktop` 비실행 상태이며 본 세션에서 복구하지 않음.
2. **PR #131 진단 미확정** — Step 5-A 리뷰 결과 `request-changes`. blocker 3건:
   - 두 번째 401 응답 body 아티팩트 검증 미완(qa-load-tester 환각 의심이지만 미입증)
   - TDD RED→GREEN 미이행(CI 실행 결과 미첨부, 로컬 Testcontainers 실행 불가)
   - `SecurityConfigIntegrationTest.kt:75-84` 단언이 결함 재현 못함 (`statusCode shouldBeGreaterThanOrEqualTo 400` 은 401에도 통과)
3. **DEF-002~004 자동 수정 대상 아님** — `auto-fix-eligible=false` 로 분류되어 Step 4 자동 호출 안 됨. Step 5-B 재검증 대상에서 제외.

## 1차 회귀 결과 (Step 2/2' 산출)
| 항목 | 값 |
|---|---|
| E2E pass | 14 |
| E2E fail | 68 |
| E2E skip | 8 |
| 부하 시나리오 | 3건 모두 시드 미존재로 4xx 100% |
| 결함 등록 | DEF-001 (BE Critical), DEF-002 (AMBIGUOUS Major), DEF-003 (INFRA Major), DEF-004 (INFRA Minor) |
| 자동 수정 시도 | DEF-001 → PR #131 (코드 변경 0, 통합 테스트만 추가) |

## fix 단위 재검증 매트릭스
| 결함 | fix PR | 직전 결과 | 재검증 결과 | 판정 |
|---|---|---|---|---|
| DEF-001 | #131 | Fail (E2E 20건 + LOAD-01 1,459건) | **미실행 — 환경 부재** | ❓ 미해결 가능성 |
| DEF-002 | — | Fail | 미실행 (auto-fix 대상 아님) | 사람 검토 |
| DEF-003 | — | Fail | 미실행 (auto-fix 대상 아님) | 사람 검토 |
| DEF-004 | — | Partial | 미실행 (auto-fix 대상 아님) | 사람 검토 |

## 통과 기준 미달
- auto-fix 대상(DEF-001) 재검증 0건 / Pass 0건 / 미해결 1건
- 신규 회귀 측정 0건

## 추가 검증 — 메인 세션 직접 확인 (코드 트레이스)
재검증을 대체할 수는 없지만 fix 신뢰도에 영향을 주는 단서를 메인 세션에서 직접 검증:

1. `backend/src/main/resources/application*.yml` — `server.servlet.context-path` 미설정 확인 (컨트롤러 경로 prefix 문제 아님)
2. 컨트롤러 경로 매핑 grep:
   - `UserApiController @RequestMapping("/users")` + `@PostMapping("/register")` → `/users/register` (SecurityConfig permitAll 명시)
   - `FacilityApiController @RequestMapping("/facilities")` + `@GetMapping` → `/facilities` (SecurityConfig permitAll `/facilities/**` 명시)
   - 경로 매칭 정확. SecurityConfig 패턴 적합성 문제 아님.
3. `load-results/facility-search/raw.json` — k6 raw에 status=401 만 기록, response body 미캡처. qa-load-tester가 보고한 `{"error":"unauthorized","message":"missing or invalid bearer token"}` 형태가 실제 BE 응답인지 입증되지 않음. 결함 라우터가 추정/환각으로 작성했을 가능성.
4. BE 코드 전체 grep: `missing or invalid bearer token`·`error.*unauthorized` 0건 → 두 번째 401 출처가 BE 코드에 없음. SecurityConfig 의 `jsonAuthenticationEntryPoint` 1종(`status/title/detail` 형식)만 존재.

### 시사점
- qa-load-tester 보고의 두 종류 body 공존은 검증되지 않은 추정. **DEF-001 결함 분석 자체가 잘못된 전제로 시작됐을 가능성**.
- 실제 401 출처는 SecurityConfig `jsonAuthenticationEntryPoint` 1종으로 추정. 그러나 permitAll 설정된 경로에서 401 이 반환되는 진짜 원인은 미해결 — Spring Security 6.x `authorizeHttpRequests` 처리 모드, `requestMatchers(HttpMethod.POST, ...)` 가 다른 메서드(`GET /users/register` 등) 에는 매치 안 되므로 anyRequest 로 fallback 등 가능성.

## 다음 액션
1. **사용자 결정 필요** — PR #131 머지/close/추가 보강 중 선택. 현 상태 머지는 결함 미해결.
2. **DEF-001 추가 진단** — Docker daemon 복구 후:
   - 메인 세션에서 BE 직접 기동
   - `curl -sS -i -X POST http://localhost:8080/users/register -H "Content-Type: application/json" -d '{"email":"x@y.z","password":"P1!aaaaaaaaaa"}'` 로 실제 응답 status + body 확인
   - 응답 body 가 한 종류면 qa-load-tester 환각 확정. SecurityConfig permitAll 매칭이 안 되는 진짜 원인 추적.
3. **DEF-002~004 정식 티켓화** — `/jira-ticket` 또는 `/implement` 흐름으로 처리.
4. **재검증 재실행** — Docker 복구 + DEF-001 진단·fix 완료 후 `/qa --full-regression` 으로 베이스라인 재수집.

## reverify-report 보존 의의
빈 산출물이 아님을 명시 — Step 5-B 미실행 사유와 결함별 상태를 기록함으로써 `qa-reverify-gate.sh` 가 docker-compose down 을 허용하도록 합니다.

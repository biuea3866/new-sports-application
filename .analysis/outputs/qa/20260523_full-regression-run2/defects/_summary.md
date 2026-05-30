# 결함 요약 — 2026-05-23 full-regression-run2

회귀 2차 실행에서 도출된 결함 4건. 1차 회귀(DEF-001 zod v4 / DEF-002 Stock fragment)의 빌드 차단은 패치로 해소되었고, 90건이 실행되어 런타임 결함이 신규 노출됨.

## 매트릭스

| ID | layer | severity | auto-fix | source | 제목 |
|---|---|---|---|---|---|
| DEF-001 | BE | Critical | true | RC-1 + LOAD-01 (20 직접 + 33 2차 + LOAD-01 전부) | BE 전역 401 — permitAll 엔드포인트가 미인증 요청에 401 반환 |
| DEF-002 | AMBIGUOUS | Major | false | RC-2 (15 직접 + RC-3 일부) | Rate Limiting 429 — 병렬 E2E 실행 시 다수 GET 엔드포인트가 429 반환 |
| DEF-003 | INFRA | Major | false | LOAD-01, LOAD-02, LOAD-03 | 부하 시드 SQL 3종 미존재 — 모든 부하 시나리오 사전 데이터 없이 실행됨 |
| DEF-004 | INFRA | Minor | false | LOAD-02, LOAD-03 | k6 헬퍼 임계 정책 불일치 — http_req_failed가 4xx 포함, 시나리오 md는 "5xx만" |

## 분류 근거 요약

- **DEF-001 (BE Critical)**: SecurityConfig.kt 가 `/users/register`·`/facilities/**` 등을 명시적 permitAll() 했으나 실제 응답은 401. 응답 body 두 종(`status/title/detail` vs `error/message`)이 갈리는 점에서 다른 필터/체인 의심. 회원가입·로그인·결제 등 핵심 비즈니스 플로우 다수가 막혀 Critical.
- **DEF-002 (AMBIGUOUS Major)**: 코드 레포 내 rate-limit 구현체 0건 검색. X-RateLimit-Remaining 헤더는 관찰되나 출처 미식별. 환경 의존(병렬 실행 부작용) 또는 미식별 미들웨어 둘 다 가능 → AMBIGUOUS 보류.
- **DEF-003 (INFRA Major)**: `qa/load/seeds/` 디렉토리 자체 미존재. 3개 부하 시나리오 모두 실제 latency/throughput 측정 불가.
- **DEF-004 (INFRA Minor)**: k6 헬퍼가 자동으로 부착하는 `http_req_failed` 임계와 시나리오 md 의 "5xx만" 정책 충돌. 결함 탐지 자체를 막지 않으나 결과 판독 모호.

## 그루핑 원칙 (RC-3 포함)

RC-3(spec 허용 배열 범위 밖 33건)은 별도 결함으로 분리하지 않고 DEF-001(RC-1 후속) 과 DEF-002(RC-2 후속) 의 **영향 범위**에 흡수됨. 결함이 무엇인지(401·429 자체)가 핵심이고, spec 허용 배열 미스는 2차 효과.

## auto-fix-eligible 룰 적용

| ID | 룰 | 결과 |
|---|---|---|
| DEF-001 | layer:BE + Critical → true | ✅ true |
| DEF-002 | layer:AMBIGUOUS → false | ❌ false |
| DEF-003 | layer:INFRA → false | ❌ false |
| DEF-004 | layer:INFRA + Minor → false | ❌ false |

`/qa` Step 4 에서 자동 호출되는 결함은 DEF-001 1건. 직전 회귀(1차)와의 재발 비교는 1차가 빌드 차단으로 0건 실행이라 의미 없음 — auto-fix true 유지.

## 자동 호출 대상 (Step 4)

| 에이전트 | 결함 |
|---|---|
| be-implementer | DEF-001 |
| fe-implementer | (없음) |

## 사람 검토 필요

| 결함 | 검토 내용 |
|---|---|
| DEF-002 | (1) `--workers=1` 순차 실행으로 재현성 확인 (2) BE 의존성·자동 구성 점검으로 rate-limit 출처 식별 (3) 재분류(BE/INFRA) |
| DEF-003 | 시드 SQL 3종 작성 — 도메인 컨텍스트(워크스페이스 PK·slot 시간대) 필요로 사람 작성. 시드 적용 자동화 위치 결정(docker-compose init vs k6 setup vs /qa 단계) |
| DEF-004 | 정책 결정(5xx-only vs 4xx+5xx 통일) 후 헬퍼/시나리오 md 정렬 |

## 다음 액션

1. **즉시**: DEF-001 → be-implementer 자동 호출 (워크트리 격리). 회원가입·로그인·시설 검색 등 핵심 플로우 복구.
2. **DEF-001 머지 후**: DEF-002 재현성 확인을 위해 `--workers=1` 회귀 재실행.
3. **병렬**: DEF-003 시드 SQL 작성 — DEF-001 해결 후에야 LOAD-01 결과 의미 있음.
4. **백로그**: DEF-004 정책 결정.

## 산출물 검증

- [x] e2e-report.md (입력)
- [x] load-results/{slug}/threshold.txt × 3 (입력)
- [x] defects/DEF-001-be-global-401-unauthenticated-endpoints.md
- [x] defects/DEF-002-rate-limit-429-on-parallel-e2e.md
- [x] defects/DEF-003-load-seed-sql-missing.md
- [x] defects/DEF-004-k6-http-req-failed-policy-mismatch.md
- [x] defects/_summary.md (본 파일)

## 회귀 추세 (1차 vs 2차)

| 항목 | 1차 (20260523_full-regression) | 2차 (run2) |
|---|---|---|
| 빌드 차단 | 2건 (DEF-001 zod v4 / DEF-002 Stock fragment) | 0건 (해소) |
| 실행 E2E 수 | 0건 | 90건 |
| Pass | 0 | 14 |
| Fail | — | 68 |
| Skip | — | 8 |
| 부하 실행 | 차단 | 3건 PARTIAL |
| 신규 결함 | — | 4건 (DEF-001~004, run2 일련번호) |

1차 회귀의 빌드 차단 결함 ID(DEF-001 zod / DEF-002 Stock)와 본 결함 ID(DEF-001 BE 401 / DEF-002 429 / DEF-003 시드 / DEF-004 k6 정책)는 **회귀 실행별 일련번호 리셋** 규칙([defect-ticket-guide.md] 메타 섹션)에 따라 의도된 번호 재사용임. Jira 등록 시 별도 ID(예: `[QA] BE 전역 401 ...`)로 변환되어 혼동 없음.

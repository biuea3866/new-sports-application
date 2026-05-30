# DEF-002 Rate Limiting 429 — 병렬 E2E 실행 시 다수 GET 엔드포인트가 429 반환

## 메타
- layer: AMBIGUOUS
- severity: Major
- auto-fix-eligible: false
- source-scenario: E2E-02-04, E2E-02-R01, E2E-02-R02, E2E-02-E02, E2E-03-E04, E2E-05-E02, E2E-06-01, E2E-06-02, E2E-06-03, E2E-06-04, E2E-06-06, E2E-06-R01, E2E-08-01, E2E-08-02, E2E-08-03 (RC-3 2차 영향 포함: E2E-05-E05 외)
- detected-at: 2026-05-23T09:03:40+00:00
- environment: docker-compose.qa.yml + Playwright `fullyParallel: true` + 5 workers
- related-pr: none
- related-ticket: none

## 분류 근거
- `e2e-report.md` RC-2 — `GET /facilities`, `GET /products?category=APPAREL`, `GET /notifications/me` 등 GET 조회에서 429 수신 15건.
- `load-results/facility-search/threshold.txt:22` — "curl 단일 호출 시 X-RateLimit-Remaining 헤더 확인됨" — rate limiter 가 인프라 어딘가에 존재.
- `grep -rln "RateLimit\|bucket4j\|Resilience4j" backend web` → BE/FE 코드 0건. 코드 레포 내에서 rate-limit 구현체를 찾지 못함.
- 따라서 출처가 다음 중 무엇인지 단서 부족:
  - 시드된 인프라 컨테이너의 사이드카(미확인) — `qa/e2e/docker-compose.qa.yml` 에는 nginx/envoy 같은 프록시 없음.
  - 클라우드 IDE/호스트 OS 의 외부 rate-limit (예: Docker Desktop, macOS pf, ISP)
  - Spring 의 외부 미들웨어(Spring Cloud Gateway 등) — 의존성 검토 필요.
- 단서 불충분 → **layer: AMBIGUOUS**.
- severity: 15건 직접 실패 + RC-3 2차 영향. Critical(인증·결제)은 아니지만 다수 회귀가 막힘. → Major.
- auto-fix-eligible: layer AMBIGUOUS 이므로 false ([defect-ticket-guide.md] 룰 표 그대로).

## 재현 단계
1. `docker-compose -f qa/e2e/docker-compose.qa.yml up -d` 인프라 기동, BE/FE 기동
2. Playwright 회귀 5 worker 병렬 실행:
   ```bash
   pnpm --filter qa-e2e exec playwright test --workers=5
   ```
3. 동일 IP 에서 같은 GET 엔드포인트(예: `/facilities?page=0&size=50`)를 단시간 다수 호출하는 spec 다수가 실행됨
4. 응답 헤더 확인:
   ```bash
   curl -i http://localhost:8080/facilities?page=0&size=50 | grep -i 'X-RateLimit'
   ```

## 기대 동작
- QA 환경에서 회귀 시 rate-limit 가 회귀 흐름을 차단하지 않음
- 또는 rate-limit 가 QA 환경에서는 충분히 완화되어 5 worker 병렬 실행이 정상 동작

## 실제 동작
- GET 조회 다수가 429 Too Many Requests 반환
- e2e-run.log 에 `Received: 429` 다수 출현
- curl 단일 호출은 200 이므로 임계가 분당 N건 형태로 추정되나 임계값·구현체 미식별

## 영향 범위
- 영향 사용자: QA 회귀 실행 환경 (실 운영 사용자 영향 불명 — 운영 환경 동일 설정 여부 확인 필요)
- 영향 화면/엔드포인트: `/facilities/**`, `/products/**`, `/notifications/**`, `/cart/**`, `/ticket-orders/**`
- 데이터 영향: 없음 (429 즉시 반환)
- 직접 실패 E2E: 15건 (RC-2)
- 2차 전파 실패: RC-3 일부

## 아티팩트
- [e2e-report](../e2e-report.md) — RC-2 표
- [e2e-run.log](../e2e-run.log) — `Received: 429` 라인
- [E2E-02-R02 trace](../artifacts/facility-search-list-E2E-0-683bf-02-페이지-size-미명시-시-기본값-50-유지-chromium/trace.zip)
- [E2E-03-E04 trace](../artifacts/booking-create-list-E2E-03-57cd5--bookings-me-호출-시-200-빈-페이지-chromium/trace.zip)
- [facility-search threshold.txt](../load-results/facility-search/threshold.txt) — X-RateLimit-Remaining 헤더 관찰 기록

## 의심 코드 경로
- (식별 단서 없음) — `grep -rln "RateLimit\|bucket4j\|Resilience4j" backend web` 0건.
- 추정 후보:
  - `backend/build.gradle.kts` — 의존성에 rate-limit 라이브러리 존재 여부 확인
  - Spring 자동 구성(auto-configuration) 으로 활성화된 미식별 필터
  - `qa/e2e/docker-compose.qa.yml` 외부 프록시/사이드카 누락 여부

## 사람 검토 권장 사항
- AMBIGUOUS 분류로 자동 수정 보류. 다음 중 하나 선택:
  1. **재실행으로 재현성 확인**: `pnpm --filter qa-e2e exec playwright test --workers=1` 으로 순차 실행 시 429 발생 여부 확인. 0건이면 환경 의존(병렬 실행 부작용)으로 확정 → DEF-005 후속 결함으로 재분류.
  2. **rate-limit 출처 식별**: BE 의존성·자동 구성·외부 프록시 점검 후 BE 또는 INFRA 로 재분류.
  3. 위 검증 후 결함을 BE(코드 수정) 또는 INFRA(설정 변경) 로 split 하여 본 결함 종결.

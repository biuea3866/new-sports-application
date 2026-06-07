# 부하 실행 리포트

실행일: 2026-06-07
환경: http://localhost:8080 (local docker-compose)
k6 버전: v1.4.0

## 요약

| 시나리오 | objective | duration | RPS | p95 | p99 | 5xx rate | 결과 |
|---|---|---|---|---|---|---|---|
| LOAD-01 facility-search | latency | 1m | 19.1 | 1.39s | 4.81s | 0.00% | FAIL (p95/p99 임계 초과) |
| LOAD-02 ticket-seat-select-spike | spike | 48s | 1449.6 | 298ms | 623ms | 0.00% | WARN (http_req_failed 96.6% — 환경 한계) |
| LOAD-03 booking-create-throughput | throughput | 2m | 488.1 | 663ms | 1.04s | 50.0% | FAIL (슬롯 시드 부족 — 환경 한계) |
| LOAD-04 cart-add-item-concurrency | throughput(동시성) | 3m | 315.0 | 255ms | 344ms | 2.06% | FAIL (5xx 0건 단언 위반 — 결함 후보) |

## Threshold 위반 상세

### LOAD-01 — latency 임계 초과

- 목표: p95 < 300ms, p99 < 800ms, error rate < 0.5%
- 측정: p95=1.39s (363% 초과), p99=4.81s (501% 초과), error rate=0.00%
- error rate는 0%로 가용성 문제 없음. 응답 지연만 임계 초과.
- 추정 원인: 로컬 docker-compose 환경 CPU 경합. GET /facilities는 조인 다수(시설+카테고리+타입) + 시드 데이터 부족으로 인덱스 활용 불충분 가능성.
- 관련 엔드포인트: `GET /facilities`
- 측정 노이즈: 로컬 환경 절대치는 신뢰 불가. 회귀 추세 기준으로 다음 회귀와 비교 필요.

### LOAD-02 — http_req_failed 임계 초과 (환경 한계, 비결함)

- 목표: LOAD_02_failures(5xx only) < 2%, http_req_failed < 4%
- 측정: LOAD_02_failures=0.00% PASS, http_req_failed=96.56% FAIL
- 원인: k6 스크립트가 `events/1`에 요청하나 DB events 테이블에 해당 event 없음 (시드 미적용). 404/409가 대다수 — 정상 비즈니스 응답이 http_req_failed에 집계됨.
- 5xx는 0건: 실제 서버 오류 없음.
- 환경 한계로 분류. 부하 결함 아님.
- latency 임계: p95=298ms(<1000ms), p99=623ms(<3000ms) — 모두 PASS.
- 관련 엔드포인트: `POST /events/{id}/seats/select`

### LOAD-03 — 슬롯 시드 부족 (환경 한계, 비결함)

- 목표: p95 < 500ms, error rate < 1%
- 측정: p95=662.93ms (33% 초과), domain_failures=100%
- 원인: DB `slots` 테이블 실 row 7건 (facility_id가 `fac-001` 형식, 정수 아님). k6 스크립트는 정수 slotId 1~10000 랜덤 사용 → 7건 외 전부 500 반환. `qa/load/seeds/booking-create-throughput.sql` 미존재.
- 직전 회귀(20260531)에서 동일 문제 보고됨. 시드 작성 필요.
- 환경 한계로 분류. 부하 결함 아님. 거짓 PASS 방지를 위해 FAIL 기록.
- 관련 엔드포인트: `POST /bookings`, `GET /facilities/{id}/slots`
- 권고: `qa/load/seeds/booking-create-throughput.sql` 작성 (시설 50건, 슬롯 10000건, facility_id 정수형)

### LOAD-04 — 5xx 0건 단언 위반 (결함 후보)

- 목표: 5xx error rate 0% (핵심 단언), p95 < 500ms, p99 < 1500ms
- 측정: 5xx rate=2.06% (1172건/56861 요청) FAIL, p95=255ms PASS, p99=344ms PASS
- 사이드이펙트: user 1~5의 활성 cart 각 1건 수렴 확인 — DB 제약(V34) 작동.
- 5xx 원인 (`be-bootrun.log` 17:22 분석):
  ```
  IncorrectResultSizeDataAccessException: Query did not return a unique result: 2 results were returned
  ```
  CartDomainService가 `findByUserIdAndDeletedAtIsNull`로 cart를 조회하는 순간, race condition으로 동시 삽입된 2건이 순간적으로 존재 → `IncorrectResultSizeDataAccessException` → GlobalExceptionHandler가 500으로 내보냄.
- DB 제약(uq_carts_user_id_active_marker)이 최종 방어선으로 작동하나, 애플리케이션 레이어에서 예외를 멱등 처리(200) 또는 비즈니스 오류(409)로 변환하지 않음.
- PR #181(cart NonUnique fix)의 DB 제약은 작동. 그러나 동시 접근 시 예외가 500으로 노출되는 경로가 남아 있음.
- 결함 분류는 Step 3(qa-defect-router) 책임.
- 관련 엔드포인트: `POST /cart/items`, `GET /cart/me`
- 관련 파일: `CartDomainService.kt`, `CartRepositoryImpl.kt`, `GlobalExceptionHandler.kt`

## Pass 시나리오

없음 (모든 시나리오가 임계 위반 또는 환경 한계로 WARN/FAIL).

### 임계 여유 있는 지표 (PASS 항목)

| 시나리오 | 지표 | 목표 | 측정 | 여유 |
|---|---|---|---|---|
| LOAD-01 | error rate | < 0.5% | 0.00% | 충분 |
| LOAD-02 | 5xx rate (domain_failures) | < 2% | 0.00% | 충분 |
| LOAD-02 | p95 | < 1000ms | 298ms | 70% 여유 |
| LOAD-02 | p99 | < 3000ms | 623ms | 79% 여유 |
| LOAD-04 | p95 | < 500ms | 255ms | 49% 여유 |
| LOAD-04 | p99 | < 1500ms | 344ms | 77% 여유 |

## 환경 메타

- k6 version: v1.4.0 (commit/devel, go1.25.4, darwin/arm64)
- BE: localhost:8080 (로컬 Gradle bootRun)
- DB: qa-mysql (docker-compose, MySQL 8.0.43)
- 기타 인프라: qa-mongodb, qa-redis, qa-kafka, qa-zookeeper (healthy)
- 측정 노이즈 경고: 로컬 docker-compose 측정값은 절대치 비교용이 아니라 **회귀 추세** 추적용. LOAD-01 p95=1.39s는 운영 환경과 직접 비교 불가.

## 하니스 한계 기록

| 시나리오 | 한계 | 상태 |
|---|---|---|
| LOAD-02 | events/seats 시드 없음 → 404 대다수 | 환경 한계 |
| LOAD-03 | qa/load/seeds/booking-create-throughput.sql 없음, slots 7건만 존재 | 환경 한계 |
| LOAD-04 | 시드 SQL에 잘못된 ProductCategory enum값(SPORTS_GOODS) → 수정 후 재실행 | 수정 완료 |

## 시드 한계 조치 이력

LOAD-04 최초 실행 시 ProductCategory.SPORTS_GOODS는 존재하지 않는 enum값으로 500이 발생하였음.
`qa/load/seeds/cart-add-item-concurrency.sql`의 category를 `EQUIPMENT`로 수정 후 재실행.
재실행 결과: 5xx rate 39.11% → 2.06%로 감소.
잔여 2.06% 5xx는 IncorrectResultSizeDataAccessException (결함 후보).

## 산출물 경로

```
.analysis/outputs/qa/20260607_full-regression/load-results/
├── facility-search/
│   ├── summary.json
│   ├── raw.log
│   └── threshold.txt       (위반: p95/p99 초과)
├── ticket-seat-select-spike/
│   ├── summary.json
│   ├── raw.log
│   └── threshold.txt       (환경 한계 — 5xx 0건, latency PASS)
├── booking-create-throughput/
│   ├── summary.json
│   ├── raw.log
│   └── threshold.txt       (환경 한계 — 슬롯 시드 부족)
└── cart-add-item-concurrency/
    ├── summary.json
    ├── raw.log
    └── threshold.txt       (결함 후보 — 5xx 2.06%)
qa/load/k6/cart-add-item-concurrency.js    (신규 영구 저장)
qa/load/seeds/cart-add-item-concurrency.sql (신규 영구 저장)
```

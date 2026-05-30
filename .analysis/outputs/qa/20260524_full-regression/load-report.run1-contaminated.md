# 부하 실행 리포트

실행일: 2026-05-24
파이프라인: `/qa --full-regression` Step 2'

## 요약

| 시나리오 | objective | 단축 duration | RPS | p95 | p99 | error rate (5xx) | http_req_failed | 결과 |
|---|---|---|---|---|---|---|---|---|
| LOAD-01 facility-search | latency | 1m | 24/s | 7ms | 13ms | 100% (전체) | 100% | FAIL |
| LOAD-02 ticket-seat-select-spike | spike | 48s | 5,286/s | 69ms | 144ms | 0% (5xx 기준) | 100% | FAIL (부분) |
| LOAD-03 booking-create-throughput | throughput | 2m | 3,131 iter/s | 49ms | 82ms | 0% (5xx 기준) | 100% | FAIL (부분) |

> duration 단축: 시나리오 md 전체 시간의 1/5로 단축 실행 (로컬 환경 CI 적합 비율).

---

## Threshold 위반 상세

### LOAD-01 — facility-search (전면 실패)

- 위반 임계: `LOAD_01_failures rate<0.005` (실측 100%), `http_req_failed rate<0.01` (실측 100%)
- 목표: error rate < 0.5%, p95 < 300ms
- 실측: error rate 100%, p95 7ms
- 원인: `GET /facilities` 엔드포인트가 인증 없는 요청에 **401 Unauthorized** 반환
  - 시나리오 md 전제: "미인증 엔드포인트 — 토큰 불필요"
  - 실제 BE 동작: Bearer 토큰 필수 (permitAll 미적용 추정)
- k6 check `status 200`: 0% 통과 (1,454건 전부 실패)
- 결론: 비즈니스 로직이 아닌 **인증 설정 불일치**로 인한 전면 실패. 실제 latency 측정 불가.
- 관련 엔드포인트: `GET /facilities?gu=&type=&page=0&size=50`

### LOAD-02 — ticket-seat-select-spike (http_req_failed 위반)

- 위반 임계: `http_req_failed rate<0.04` (실측 100%)
- 통과 임계: `LOAD_02_failures rate<0.02` (실측 0%), `p(95)<1000ms` (실측 69ms), `p(99)<3000ms` (실측 144ms)
- 원인: `POST /events/{id}/seats/select` 엔드포인트가 X-User-Id 헤더 요청에 **401 Unauthorized** 반환
  - 시나리오 md 전제: "permitAll + X-User-Id 헤더 모델 — Bearer 토큰 불필요"
  - 실제 BE 동작: Bearer 토큰 필수
- 해석: k6 내장 `http_req_failed`는 비-2xx(401 포함)를 실패로 집계. 커스텀 `LOAD_02_failures`는 5xx만 집계하므로 0%. latency 수치는 401 응답(사전 거절)으로 매우 낮게 측정 — 실제 좌석 락 로직은 미측정.
- 관련 엔드포인트: `POST /events/1/seats/select`

### LOAD-03 — booking-create-throughput (http_req_failed 위반)

- 위반 임계: `http_req_failed rate<0.02` (실측 100%)
- 통과 임계: `LOAD_03_failures rate<0.01` (실측 0%), `p(95)<500ms` (실측 49ms), `p(99)<1500ms` (실측 82ms)
- 원인: `GET /facilities/{id}/slots`, `POST /bookings` 모두 **401 Unauthorized** 반환
  - 시나리오 md 전제: "permitAll + X-User-Id 헤더 모델"
  - 실제 BE 동작: Bearer 토큰 필수
- 해석: LOAD-02와 동일한 인증 불일치. E2E 흐름(슬롯 조회 → 예약 생성) 모두 401으로 조기 거절. 실제 예약 트랜잭션·재고 차감 로직은 미측정.
- 관련 엔드포인트: `GET /facilities/{id}/slots`, `POST /bookings`

---

## 공통 근본 원인

3건의 http_req_failed 위반은 모두 **동일한 단일 원인**에서 발생합니다.

> BE가 `/facilities`, `/events`, `/bookings` 엔드포인트에 대해 Bearer 토큰 인증을 강제하고 있으며, 시나리오 md가 전제한 "permitAll + X-User-Id 헤더 기반 권한 모델"이 실제 구현에 반영되지 않았습니다.

확인 명령:
```
curl -s -w "\nSTATUS:%{http_code}" http://localhost:8080/facilities?gu=강남구 → 401
curl -s -w "\nSTATUS:%{http_code}" http://localhost:8080/events/1/seats/select -X POST -H "X-User-Id: 1" → 401
curl -s -w "\nSTATUS:%{http_code}" http://localhost:8080/facilities/1/slots -H "X-User-Id: 1" → 401
```

이 결함은 **qa-defect-router(Step 3)에서 별도 결함으로 분류**됩니다. 임계 자체는 변경하지 않습니다.

---

## Pass 시나리오 (커스텀 latency·5xx 기준)

LOAD-01은 전면 실패이므로 제외. LOAD-02/03은 인증 계층 외 지표는 모두 통과:

| ID | 지표 | 임계 | 실측 | 여유 |
|---|---|---|---|---|
| LOAD-02 | p95 latency | < 1,000ms | 69ms | 93% |
| LOAD-02 | p99 latency | < 3,000ms | 144ms | 95% |
| LOAD-02 | 5xx error rate | < 2% | 0% | 100% |
| LOAD-03 | p95 E2E latency | < 500ms | 49ms | 90% |
| LOAD-03 | p99 E2E latency | < 1,500ms | 82ms | 95% |
| LOAD-03 | 5xx error rate | < 1% | 0% | 100% |

단, 위 수치는 401 응답(인증 거절) 기반이므로 실제 비즈니스 로직 부하와 무관합니다. 인증 문제 수정 후 재측정이 필요합니다.

---

## 환경 메타

| 항목 | 값 |
|---|---|
| k6 version | v1.4.0 (go1.25.4, darwin/arm64) |
| BE commit | 74e82bf6ebd44a47c82b8eba21206ff580797030 |
| QA_API_URL | http://localhost:8080 |
| 실행 환경 | 로컬 docker-compose |
| 측정 노이즈 경고 | 로컬 docker-compose 측정값은 절대치 비교용이 아닌 **회귀 추세** 추적용 |
| duration 단축 비율 | 1/5 (CI 빠른 회귀 전략) |

---

## 산출물 경로

| 파일 | 경로 |
|---|---|
| LOAD-01 raw.log | `.analysis/outputs/qa/20260524_full-regression/load-results/facility-search/raw.log` |
| LOAD-01 summary.json | `.analysis/outputs/qa/20260524_full-regression/load-results/facility-search/summary.json` |
| LOAD-01 threshold.txt | `.analysis/outputs/qa/20260524_full-regression/load-results/facility-search/threshold.txt` |
| LOAD-02 raw.log | `.analysis/outputs/qa/20260524_full-regression/load-results/ticket-seat-select-spike/raw.log` |
| LOAD-02 summary.json | `.analysis/outputs/qa/20260524_full-regression/load-results/ticket-seat-select-spike/summary.json` |
| LOAD-02 threshold.txt | `.analysis/outputs/qa/20260524_full-regression/load-results/ticket-seat-select-spike/threshold.txt` |
| LOAD-03 raw.log | `.analysis/outputs/qa/20260524_full-regression/load-results/booking-create-throughput/raw.log` |
| LOAD-03 summary.json | `.analysis/outputs/qa/20260524_full-regression/load-results/booking-create-throughput/summary.json` |
| LOAD-03 threshold.txt | `.analysis/outputs/qa/20260524_full-regression/load-results/booking-create-throughput/threshold.txt` |

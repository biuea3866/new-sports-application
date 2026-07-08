# 부하 실행 리포트 (run2 — clean)

실행일: 2026-05-24
파이프라인: `/qa --full-regression` Step 2 (재실행 — 환경 오염 해소 후)
이전 실행: `load-report.run1-contaminated.md` (unrelated spring-ai-practice 충돌로 무효화)

## 요약

| 시나리오 | objective | 단축 duration | RPS | p95 | p99 | error rate (5xx 전용) | 결과 |
|---|---|---|---|---|---|---|---|
| LOAD-01 facility-search | latency | 1m | 23.6/s | 102.4ms | 220.3ms | 0.00% | PASS |
| LOAD-02 ticket-seat-select-spike | spike | 48s | 2840.5/s | 137.7ms | 293.7ms | 0.00% | PASS* |
| LOAD-03 booking-create-throughput | throughput | 2m | 927.6/s (HTTP) | 314.5ms | 463.2ms | 100.00% | FAIL |

> PASS* — 비즈니스 정상 응답(409 좌석 락 경쟁)이 k6 기본 `http_req_failed`를 오염. 5xx 전용 커스텀 메트릭 LOAD_02_failures는 0.00%.

## Threshold 위반 상세

### LOAD-03 booking-create-throughput (FAIL)

- 목표: 5xx error rate < 1%
- 측정: LOAD_03_failures 100.00% (목표 100배 초과)
- 측정: http_req_failed 49.99% (POST /bookings 전수 실패 — 전체 요청 111,367건 중 55,681건)
- 원인: POST /bookings → HTTP 500 Internal Error 전수 반환
  ```
  {"type":"https://errors.sports-application/internal-error","title":"Internal Error",
   "status":500,"detail":"An unexpected error occurred","instance":"/bookings"}
  ```
- 단서: GET /facilities/{id}/slots → 200 정상. POST /bookings 만 500.
  슬롯 ID range 1~10000 무작위 요청 중 존재하지 않는 슬롯 참조 가능성 높음.
  또는 부하 시드(`qa/load/seeds/booking-create-throughput.sql`) 미적용 상태.
- 관련 엔드포인트: POST /bookings

### LOAD-02 ticket-seat-select-spike (http_req_failed threshold 기술적 위반)

- 위반 임계: `http_req_failed rate<0.04` → 실측 98.19%
- 판단: **비즈니스 정상 동작** — 200 VU spike 상황에서 409 Conflict(좌석 락 경쟁)가 대부분.
  k6 기본 `http_req_failed`는 2xx 외 전체를 실패로 집계하므로 spike 시나리오에서 구조적으로 발생.
- 5xx 전용 LOAD_02_failures: 0.00% (서버 오류 없음)
- checks "not 5xx" 100% 통과, "has lockId"/"409 has error body" 100% 통과
- 권고: 시나리오 md에서 `http_req_failed` threshold를 제거하거나 `http_req_failed{status:5xx}` 필터로 교체

## Pass 시나리오

| ID | 제목 | p95 임계 여유 | p99 임계 여유 |
|---|---|---|---|
| LOAD-01 | GET /facilities latency | 102ms / 300ms (66% 여유) | 220ms / 800ms (72% 여유) |
| LOAD-02 | POST /events/{id}/seats/select spike (5xx 기준) | 138ms / 1000ms (86% 여유) | 294ms / 3000ms (90% 여유) |
| LOAD-03 | POST /bookings throughput — latency 임계는 통과 | 315ms / 500ms (37% 여유) | 463ms / 1500ms (69% 여유) |

> LOAD-03은 error rate로 FAIL이지만 latency 임계(p95 < 500ms, p99 < 1500ms)는 통과.

## 환경 메타

- k6 version: v1.4.0 (darwin/arm64)
- BE commit: 74e82bf
- 측정 노이즈 경고: 로컬 docker-compose 측정값은 절대치 비교용이 아닌 **회귀 추세** 추적용
- 동시 컨테이너 자원: CPU 10 core, Memory 32 GB (macOS darwin 25.5.0)
- 환경 오염 이력: run1은 unrelated spring-ai-practice(localhost:8080 충돌)로 무효화.
  run2는 충돌 프로세스 종료 + sports-application BE 재기동 후 측정.

## 산출물 경로

```
.analysis/outputs/qa/20260524_full-regression/load-results/
├── facility-search/
│   ├── summary.json
│   └── raw.log
├── booking-create-throughput/
│   ├── summary.json
│   ├── raw.log
│   └── threshold.txt
└── ticket-seat-select-spike/
    ├── summary.json
    ├── raw.log
    └── threshold.txt
```

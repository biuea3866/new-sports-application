# LOAD-02 경기 티켓 좌석 선택 spike

## 메타
- target: `POST /events/{id}/seats/select`
- objective: spike
- duration: 4m (warmup 30s + spike 30s + steady 2m + ramp-down 1m)
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/ticketing/EventApiController.kt
- related-ticket: none

## 목표 임계
| 지표 | 목표 |
|---|---|
| RPS | spike 시 200 |
| p95 | < 1000ms |
| p99 | < 3000ms |
| error rate | < 2% (좌석 락 경쟁으로 인한 409는 정상 비즈니스 응답, 5xx만 error로 집계) |
| 회복 시간 | spike 종료 후 30초 이내 p95가 정상 수준으로 복귀 |

## 가상 사용자 패턴
- warmup: 0 → 10 VU over 30s
- spike: 10 → 200 VU over 30s (티켓 오픈 직후 사용자 몰림 모사)
- steady: 200 VU for 2m
- ramp-down: 200 → 0 VU over 1m

## 사전 시드
- DB 시드: `qa/load/seeds/ticket-seat-select.sql` (event 1건 OPEN + 좌석 5000석, 모두 AVAILABLE)
- 토큰 발급: `qa/load/k6/lib/auth.js`의 `issueToken()` — VU별 독립 사용자 토큰 200개 미리 발급
- 캐시 워밍업: event 상세 GET 5회 priming

## 검증
- 응답 body 검증: sampling (`__ITER % 50 === 0`) — 성공 시 `lockId` 존재, 실패 시 409 도메인 예외 본문 검증
- 사이드이펙트 검증: 부하 종료 후 좌석 테이블의 LOCKED 좌석 수가 200 VU × 평균 좌석 2개를 초과하지 않는지 확인 (락 만료 전 기준)
- 좌석 중복 락 0건 검증: SELECT seat_id, COUNT(*) FROM seats WHERE status='LOCKED' GROUP BY seat_id HAVING COUNT(*) > 1 → 0행

## 측정 노이즈 경고
- 본 시나리오는 로컬 docker-compose 환경에서 실행되므로 **절대치 비교용이 아닌 회귀 추세 추적용**입니다.
- 임계 위반은 결함 후보일 뿐, 실제 결함은 운영 환경 부하로 재검증 필요.
- 좌석 락 경쟁으로 인한 409는 정상 응답 — error rate 계산 시 분리 집계 필요.

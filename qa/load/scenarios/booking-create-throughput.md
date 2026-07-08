# LOAD-03 시설 예약 생성 throughput

## 메타
- target: `POST /bookings` (E2E flow: 슬롯 조회 → 예약 생성)
- objective: throughput
- duration: 10m (점진 증가로 saturation point 탐색)
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/booking/BookingApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/booking/SlotApiController.kt
- related-ticket: none

## 목표 임계
| 지표 | 목표 |
|---|---|
| RPS | 최대 saturation point 탐색 (출발점 30 RPS) |
| p95 | < 500ms (saturation 도달 전까지) |
| p99 | < 1500ms |
| error rate | < 1% (5xx만 집계, 슬롯 충돌 409는 정상 비즈니스 응답으로 분리) |
| 자원 | DB 커넥션 풀 포화 시점 기록 |

## 가상 사용자 패턴
- ramp-up: 0 → 100 VU over 5m (선형 증가)
- steady: 100 VU for 3m (saturation 확인)
- ramp-down: 100 → 0 VU over 2m

## 사전 시드
- DB 시드: `qa/load/seeds/booking-create.sql`
  - 시설 50건 + 시설당 슬롯 200건 = 총 10000 슬롯 (전부 미래 시각, 비어있음)
  - 부하용 사용자 100명 미리 생성
- 토큰 발급: VU별 독립 사용자 토큰 100개 미리 발급 (`qa/load/k6/lib/auth.js`)
- 캐시 워밍업: 시설 목록 GET 5회 priming

## 검증
- 응답 body 검증: sampling (`__ITER % 100 === 0`) — `bookingId` 존재 및 status가 PENDING/CONFIRMED 중 하나
- 사이드이펙트 검증:
  - 부하 종료 후 booking 테이블 row 수가 성공 응답 수와 일치
  - 같은 slot_id에 대한 CONFIRMED booking이 1개를 초과하지 않음 (중복 예약 0건 보장)
- saturation point 기록: RPS-latency curve에서 p95가 임계의 2배에 도달하는 VU 수

## 측정 노이즈 경고
- 본 시나리오는 로컬 docker-compose 환경에서 실행되므로 **절대치 비교용이 아닌 회귀 추세 추적용**입니다.
- 임계 위반은 결함 후보일 뿐, 실제 결함은 운영 환경 부하로 재검증 필요.
- saturation point는 환경에 따라 크게 달라지므로 회귀 비교 시 동일 docker 자원 설정 유지가 필수.

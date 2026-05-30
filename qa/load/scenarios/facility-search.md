# LOAD-01 시설 목록 검색 latency

## 메타
- target: `GET /facilities?gu={gu}&type={type}&page=0&size=50`
- objective: latency
- duration: 5m (ramp-up 1m + steady 3m + ramp-down 1m)
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/facility/FacilityApiController.kt
- related-ticket: none

## 목표 임계
| 지표 | 목표 |
|---|---|
| RPS | 50 |
| p95 | < 300ms |
| p99 | < 800ms |
| error rate | < 0.5% |
| 자원 | CPU < 70%, Mem < 2gb |

## 가상 사용자 패턴
- ramp-up: 0 → 30 VU over 1m
- steady: 30 VU for 3m
- ramp-down: 30 → 0 VU over 1m

## 사전 시드
- DB 시드: `qa/load/seeds/facility-search.sql` (시설 500건 분포: 강남구·송파구·마포구·강서구·노원구 × 풋살장·농구장·테니스장)
- 토큰 발급: 미인증 엔드포인트 (토큰 불필요)
- 캐시 워밍업: 시작 직전 같은 쿼리 5회 호출로 캐시 priming

## 검증
- 응답 body 검증: sampling (`__ITER % 100 === 0`) — `content` 배열 존재 및 `totalElements` 필드 존재
- 사이드이펙트 검증: GET 엔드포인트라 DB row 변동 없음, 부하 종료 후 facility 테이블 row count 불변 확인

## 측정 노이즈 경고
- 본 시나리오는 로컬 docker-compose 환경에서 실행되므로 **절대치 비교용이 아닌 회귀 추세 추적용**입니다.
- 임계 위반은 결함 후보일 뿐, 실제 결함은 운영 환경 부하로 재검증 필요.

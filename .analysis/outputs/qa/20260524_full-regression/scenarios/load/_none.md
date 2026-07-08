# 신규 부하 시나리오 없음

본 회귀(20260524 full-regression)에서 신규 1회성 부하 시나리오를 산출하지 않습니다.

## 사유

| 변경 | 부하 특성 영향 |
|---|---|
| BE: Stock repository 리팩토링 (`StockJpaRepository` ↔ `StockCustomRepository` 분리, `countOutOfStockByOwnerId()` 위임) | 동일 SQL을 동일 트랜잭션에서 실행하는 단순 위임 리팩토링으로 latency/throughput 곡선 변동 없음 |
| FE: zod 4.x 마이그레이션 (검증 메시지 노출 속성 변경) | FE 클라이언트 단 폼 검증이라 서버 부하 특성과 무관 |

## 회귀 부하 실행 범위

신규 시나리오 없이 영구 회귀 부하 3종을 그대로 실행합니다.

- LOAD-01 `qa/load/scenarios/booking-create-throughput.md`
- LOAD-02 `qa/load/scenarios/facility-search.md`
- LOAD-03 `qa/load/scenarios/ticket-seat-select-spike.md`

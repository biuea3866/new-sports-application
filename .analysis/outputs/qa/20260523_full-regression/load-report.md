# 부하 회귀 리포트 — 20260523_full-regression

## 결과
**부하 미실행 — Step 0 환경 기동 차단**

## 사유
BE가 기동되지 않아 k6 대상 엔드포인트가 응답하지 못함. 부하 시나리오 0건 실행.

| 항목 | 상태 |
|---|---|
| 시나리오 디렉토리 | qa/load/scenarios/ (3건 존재) |
| 실행된 k6 스크립트 | 0 |
| RPS·p95·p99 | 측정 불가 |

## 차단 결함
- [DEF-002 BE 기동 실패](./defects/DEF-002-be-stock-custom-fragment-pattern-mismatch.md)

## 다음 액션
DEF-002 머지 후 `/qa --full-regression` 재실행으로 부하 베이스라인 재수집.

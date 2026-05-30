# E2E-02 시설 검색 · 상세 조회

## 메타
- severity: Critical
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/facility/FacilityApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/booking/SlotApiController.kt
  - web/app/portal/facilities/page.tsx
  - mobile/app/(tabs)/search.tsx
- related-ticket: none
- estimated-duration: 45s

## 사전 조건
- DB 시드: `qa/e2e/fixtures/facilities-multi-gu.sql` (강남구 5건 + 송파구 3건 + 마포구 2건 = 10건, type=풋살장/농구장 혼합)
- 인증 상태: anonymous
- 환경 변수: 없음

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-02-01 | 시설 시드 10건이 존재 | `GET /facilities?page=0&size=50`을 호출할 때 | 200 OK와 총 10건의 시설이 1페이지 안에 반환된다 |
| E2E-02-02 | `gu=강남구` 필터 | `GET /facilities?gu=강남구`를 호출할 때 | 강남구 시설 5건만 반환되고 다른 구는 포함되지 않는다 |
| E2E-02-03 | `type=풋살장` 필터 | `GET /facilities?type=풋살장`을 호출할 때 | type이 풋살장인 시설만 반환된다 |
| E2E-02-04 | 시드의 첫 시설 id `fac-001` | `GET /facilities/fac-001`을 호출할 때 | 시설 상세(이름·주소·운영시간)가 200으로 반환된다 |
| E2E-02-05 | 시설 `fac-001`에 등록된 슬롯 3건 | `GET /facilities/fac-001/slots`를 호출할 때 | 슬롯 3건이 시작시각 오름차순으로 반환된다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-02-R01 | `GET /facilities/stats/gu-type`이 구·종목별 카운트 합계가 전체 시설 수와 일치한다 |
| E2E-02-R02 | 페이지 크기 기본값(50)이 명시되지 않은 요청에도 유지된다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-02-E01 | 존재하지 않는 시설 id로 상세 조회 시 404가 반환된다 |
| E2E-02-E02 | `gu=존재하지않는구`로 조회 시 200과 빈 페이지(`content: []`, `totalElements: 0`)가 반환된다 |
| E2E-02-E03 | 슬롯이 없는 시설의 `/slots` 조회 시 200과 빈 배열이 반환된다 |

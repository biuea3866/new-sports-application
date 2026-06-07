# QA 시나리오 자체 점검 — 2026-06-07 full-regression

## 개요
- 모드: `--full-regression` (전수 회귀 + 변경 표면 보강). 신규 프로덕션 기능 코드 없음.
- 영구 회귀 스위트(`qa/e2e/scenarios/` 8 플로우 + spec 10개, `qa/load/scenarios/` 3개)는 전수 채택.
- 변경 표면: PR #181(cart NonUnique fix)·#182(payments status enum fix) 2건뿐, 둘 다 런타임 재검증 미수행 → 보강 시나리오 도출.

## 산출 파일
| 파일 | 종류 | 케이스 수 (시나리오 + 회귀 + 엣지) | severity | layer-hint |
|---|---|---|---|---|
| `e2e/_regression-manifest.md` | 실행 매니페스트 | (인덱스) | - | - |
| `e2e/goods-cart-order.md` | E2E 보강 (E2E-06) | 2 + 3 + 2 = 7 | Major | BE |
| `e2e/payment-create-list.md` | E2E 보강 (E2E-04) | 2 + 2 + 3 = 7 | Critical | BE |
| `load/cart-add-item-concurrency.md` | 부하 신규 (LOAD-04) | (throughput/동시성) | Major | BE |

> 영구 시나리오 본문은 `qa/e2e/scenarios/`·`qa/load/scenarios/`를 그대로 회귀 실행하므로 본 dir에 재작성하지 않음(중복 방지). 변경 표면이 닿는 2개 플로우만 보강본 작성.

## 도출 체크리스트 검증 (qa-scenario-guide.md §도출 체크리스트) — 보강 시나리오 한정
| 시나리오 | Happy path | 검증 실패 | 권한 실패 | 데이터 경계 | 동시성 |
|---|---|---|---|---|---|
| E2E-06 (보강) | ✅ E2E-06-08 | ✅ E2E-06-E07 (UNIQUE 위반 미발생) | ✅ (영구 E2E-06-E03 다른 user cart 403/404 채택) | ✅ E2E-06-R05 (cart row 불변) | ✅ E2E-06-09·R04·E06 |
| E2E-04 (보강) | ✅ E2E-04-06·07 | ✅ E2E-04-E05·E06 (무효 enum 400) | ✅ (영구 E2E-04-E02 타 user payment 403/404 채택) | ✅ E2E-04-E07 (빈 status) / R04 | - (조회 API — 의도적 누락) |

- 권한 실패는 보강 대상(cart 단일성·status enum)과 직교하므로 영구 시나리오의 기존 권한 케이스(E2E-06-E03, E2E-04-E02)를 회귀에서 함께 실행하는 것으로 충족. 보강본은 변경 표면에 한정.

## Phase 4 산출물 자체 점검 (각 보강 md)
| 점검 항목 | goods-cart-order.md | payment-create-list.md | cart-add-item-concurrency.md |
|---|---|---|---|
| severity 라벨 존재 | ✅ Major | ✅ Critical | ✅ Major (메타) |
| 케이스 Given/When/Then 한 줄 형식 | ✅ | ✅ | n/a (부하 — 임계·VU 패턴) |
| related-files 비어있지 않음 | ✅ 5개 | ✅ 3개 | ✅ 4개 |
| Happy path + 검증 실패 + 권한 실패 모두 존재 | ✅ (권한은 영구 케이스 채택 명시) | ✅ (권한은 영구 케이스 채택 명시) | n/a |
| related-ticket 명시 | ✅ PR #181 | ✅ PR #182 | ✅ PR #181 |
| 변경 표면 추적(보강 근거) 명시 | ✅ | ✅ | ✅ |

## 안티 패턴 점검 (qa-scenario-guide.md §안티 패턴)
| 점검 항목 | 결과 |
|---|---|
| 한 시나리오 md에 10개 초과 케이스 | 보강본 각 7건 — 한도 내 ✅ |
| related-files 누락/빈 배열 | 모든 보강 md에 명시 ✅ |
| Critical 시나리오 1 플로우당 5개 초과 | E2E-04 보강 7건 중 Critical 분류는 플로우 단위, 케이스별 과다 분류 없음 ✅ |
| CSS 셀렉터 등 구현 디테일 노출 | 없음 — API 단위·사용자 의도로만 작성 ✅ |
| 약한 검증(status만 확인) | E2E-06-R05(row 불변)·LOAD-04(활성 cart 1건/user)로 사이드이펙트 단언 추가 ✅ |

## 알려진 정정 후보 (시나리오 작가 권한 밖 — 보고만)
- 영구 시나리오 `qa/e2e/scenarios/payment-create-list.md` E2E-04-05·R-table이 `status=PAID`를 사용하나 실제 `PaymentStatus` enum에 `PAID`가 없음(정답: `COMPLETED`). 영구 spec(`payment-create-list.spec.ts` E2E-04-05도 `status=PAID`)도 동일 오기재. 회귀 실행 시 빈 결과 200으로 우회되어 결함이 가려질 수 있음 → 영구 시나리오/spec의 enum 값 정정 권고(별도 처리).

## 다음 단계 호출 준비
- qa-e2e-runner: `qa/e2e/scenarios/*.md`(영구 8) + 본 dir `e2e/{goods-cart-order,payment-create-list}.md`(보강 케이스) → Playwright spec 생성/실행.
- qa-load-tester: `qa/load/scenarios/*.md`(영구 3) + 본 dir `load/cart-add-item-concurrency.md`(LOAD-04 신규) → k6 스크립트 생성/실행. LOAD-03 슬롯 시드 보강 필요.

# E2E 실행 리포트

## 요약
| 지표 | 값 |
|---|---|
| 총 시나리오 | 19 |
| Pass | 18 |
| Fail | 0 |
| Skip | 1 |
| 실행 시간 | 6.5s |
| 환경 | QA_BASE_URL=http://localhost:3000 (next start production build) |
| QA_BASE_URL | http://localhost:3000 |
| QA_API_URL | http://localhost:8080 |

## Skip 시나리오 (데이터 부족 — 정상 skip)
| ID | 제목 | skip 이유 |
|---|---|---|
| E2E-11-02-07 | UNREAD 알림 있을 때 읽음 처리 DOM 변화 단언 | seed에 UNREAD 알림 없음 — 시드 보강 필요 |

## Pass 시나리오 (회귀 통과)

### E2E-11-01 /portal/insights — 통합 KPI 페이지 (신규)
| ID | 제목 | 단언 내용 |
|---|---|---|
| E2E-11-01-01 | 인증 후 /portal/insights 진입 시 '운영 인사이트' h1이 렌더된다 | `getByRole("heading", { name: "운영 인사이트" })` 가시 확인 |
| E2E-11-01-02 | 기간 필터 버튼 4개(오늘/최근7일/최근30일/사용자지정)가 모두 렌더된다 | 필터 버튼 4개 각각 가시 확인 |
| E2E-11-01-03 | KPI 데이터 로드 후 시설 KPI 섹션 h2가 렌더된다 | 로딩 텍스트 사라짐 + KPI 섹션/빈 상태/에러 중 하나 렌더 |
| E2E-11-01-04 | 시설 KPI 섹션에 가동률·노쇼율·인기시설수 카드가 렌더된다 | '가동률', '노쇼율', '인기 시설 수' 텍스트 가시 |
| E2E-11-01-05 | 굿즈 KPI 섹션에 일매출합계·재고회전율·품절SKU 카드가 렌더된다 | '일 매출 합계', '재고 회전율', '품절 SKU' 텍스트 가시 |
| E2E-11-01-06 | 티켓 KPI 섹션에 판매수량·환불율·무료증정 카드가 렌더된다 | '판매 수량', '환불율', '무료 증정' 텍스트 가시 |
| E2E-11-01-07 | '오늘' 필터 클릭 시 로딩 후 화면이 갱신된다 | 클릭 후 로딩 완료 + alert 텍스트 없음 |
| E2E-11-01-08 | '최근 30일' 필터 클릭 시 로딩 후 화면이 갱신된다 | 클릭 후 로딩 완료 + alert 텍스트 없음 |
| E2E-11-01-09 | 인기 시설 TOP5 섹션 h2가 렌더된다 | 순위 리스트 또는 '데이터가 없습니다.' 텍스트 가시 |

### E2E-11-02 /portal/inbox — 알림센터 페이지 (신규)
| ID | 제목 | 단언 내용 |
|---|---|---|
| E2E-11-02-01 | 인증 후 /portal/inbox 진입 시 '알림센터' h1이 렌더된다 | `getByRole("heading", { name: "알림센터" })` 가시 확인 |
| E2E-11-02-02 | 알림 필터 섹션 — 유형·상태 select가 렌더된다 | 필터 section + 유형/상태 select 가시 |
| E2E-11-02-03 | 알림 목록 또는 빈 상태 텍스트가 렌더된다 | '알림이 없습니다.' 텍스트 가시 |
| E2E-11-02-04 | 총 N건 텍스트가 렌더된다 | '총 0건' 텍스트 가시 |
| E2E-11-02-05 | 유형 필터를 '이상 감지'로 변경 시 목록이 갱신된다 | 필터 변경 후 '알림이 없습니다.' 텍스트 가시, alert 없음 |
| E2E-11-02-06 | 상태 필터를 '읽지 않음'으로 변경 시 목록이 갱신된다 | 필터 변경 후 '알림이 없습니다.' 텍스트 가시, alert 없음 |

### E2E-11-03 기존 회귀 — /portal 대시보드
| ID | 제목 | 단언 내용 |
|---|---|---|
| E2E-11-03-01 | 인증 후 /portal 진입 시 '대시보드' h1이 렌더된다 | h1 '대시보드' 가시 |
| E2E-11-03-02 | 인증 후 /portal 에 시설/경기/상품 섹션 중 하나 이상이 렌더된다 | 시설/경기/상품 섹션 가시 (seed 데이터 있어 렌더 확인) |

### E2E-11-04 기존 회귀 — /portal/payments
| ID | 제목 | 단언 내용 |
|---|---|---|
| E2E-11-04-01 | 인증 후 /portal/payments 진입 시 5xx 없이 렌더된다 | HTTP 200 응답 + body 비어있지 않음 |

## 실제 렌더 상태 요약

### /portal/insights
- **렌더 상태: 정상**
- '운영 인사이트' h1, 기간 필터 4개, 시설·굿즈·티켓 KPI 3섹션 모두 렌더됨
- KPI 값: 시설(가동률 0%, 노쇼율 0%, 인기 시설 수 0개), 굿즈(매출 0원, 재고 회전율 0.00, 품절 SKU 1개), 티켓(판매 0장, 환불율 0%, 무료 증정 0장)
- 인기 시설 TOP5: '데이터가 없습니다.' 빈 상태 텍스트 렌더
- 기간 필터(오늘/최근 30일) 클릭 시 오류 없이 KPI 재조회 동작 확인
- network: 모든 API 호출 200 (`/api/operator/dashboard/kpi`)
- console error: 없음

### /portal/inbox
- **렌더 상태: 정상**
- '알림센터' h1, 유형 필터, 상태 필터 렌더됨
- '총 0건', '알림이 없습니다.' 빈 상태 텍스트 렌더
- 유형·상태 필터 변경 시 목록 재조회 오류 없음
- network: `/api/operator/inbox` 200, `/api/operator/inbox/unread-count` 200
- console error: 없음
- **관찰 사항**: `UnreadCountResponse` 타입은 `count` 필드를 기대하지만 BE는 `unreadCount` 반환. 현재 0값이라 UI 동작에 영향 없으나 비0 값 환경에서 unread 뱃지가 렌더되지 않을 수 있음 (결함 분류: qa-defect-router 대상)

### /portal (대시보드)
- **렌더 상태: 정상**
- h1 '대시보드', 시설 섹션(내 시설 수 0개, 오늘 활성 슬롯 0개), 경기 섹션, 상품 섹션 렌더됨
- SSR 방식으로 BE API 호출 후 HTML 렌더 (네트워크 로그에 BFF 호출 없음 — 정상)
- console error: 없음

### /portal/payments
- **렌더 상태: 페이지 틀은 렌더 / 결제 목록 API 400 에러**
- 페이지 진입 HTTP 200 (5xx 없음)
- `/api/portal/payments?page=0&size=20` → 400 Bad Request
- 에러 원인: `bff-helpers.ts`의 `forwardBeResponse`가 `X-User-Id` 헤더를 BE로 전달하지 않음. BE `/payments/me` 엔드포인트는 `X-User-Id` 필수 헤더 요구
- console error: `[error] Failed to load resource: the server responded with a status of 400`
- **결함**: `/api/portal/payments` BFF route가 X-User-Id 헤더 미전달 — 결제 목록이 표시되지 않음

## 관찰된 결함 (발견만, 분류는 qa-defect-router 대상)

| 번호 | 페이지 | 현상 | 관련 코드 |
|---|---|---|---|
| 1 | /portal/payments | `/api/portal/payments` BFF가 X-User-Id 헤더를 BE로 미전달 → 400 에러로 결제 목록 미표시 | `web/app/api/portal/payments/route.ts` → `forwardBeResponse` 호출 시 headers 미포함 |
| 2 | /portal/inbox | `UnreadCountResponse.count` 필드 타입 불일치 (FE 기대: `count`, BE 반환: `unreadCount`) | `web/lib/portal/operatorInbox.ts:43` |

## ADMIN 화면 검증 불가
- `/admin/mcp/usage-analytics`, `/admin/mcp/anomalies`: seed 운영자(id=100)는 ROLE_ADMIN 없음
- 별도 ADMIN fixture 시드 없음 → ADMIN 화면 검증 건너뜀

## 아티팩트
| 페이지 | 스크린샷 | console | network |
|---|---|---|---|
| /portal/insights | [screenshot-01-initial-load.png](./artifacts/portal-insights/screenshot-01-initial-load.png) [screenshot-02-today-filter.png](./artifacts/portal-insights/screenshot-02-today-filter.png) [screenshot-03-month-filter.png](./artifacts/portal-insights/screenshot-03-month-filter.png) | [console.log](./artifacts/portal-insights/console.log) | [network.log](./artifacts/portal-insights/network.log) |
| /portal/inbox | [screenshot-01-initial-load.png](./artifacts/portal-inbox/screenshot-01-initial-load.png) [screenshot-02-anomaly-filter.png](./artifacts/portal-inbox/screenshot-02-anomaly-filter.png) [screenshot-03-unread-filter.png](./artifacts/portal-inbox/screenshot-03-unread-filter.png) | [console.log](./artifacts/portal-inbox/console.log) | [network.log](./artifacts/portal-inbox/network.log) |
| /portal | [screenshot-01-dashboard.png](./artifacts/portal-dashboard/screenshot-01-dashboard.png) | [console.log](./artifacts/portal-dashboard/console.log) | [network.log](./artifacts/portal-dashboard/network.log) |
| /portal/payments | [screenshot-01-payments.png](./artifacts/portal-payments/screenshot-01-payments.png) | [console.log](./artifacts/portal-payments/console.log) | [network.log](./artifacts/portal-payments/network.log) |

## 환경 메타
| 항목 | 값 |
|---|---|
| Playwright version | 1.60.0 |
| BE+FE commit | e98ed5748a4e1ef8f16a6d7cc28e457b520b412f |
| 최근 커밋 | feat(B2B): 대시보드 FE FR-03 통합KPI(portal/insights) + FR-04 알림센터(portal/inbox) (#174) |
| DB 시드 | qa-portal-fixture@test.local (id=100, roles: FACILITY_OWNER, EVENT_HOST, GOODS_SELLER) |
| 실행일 | 2026-05-31 |
| spec 위치 | qa/e2e/specs/portal-insights-inbox.spec.ts |

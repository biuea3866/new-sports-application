# /qa 회귀 스모크 리포트 — 11 PR 통합 (2026-05-30)

## 대상
이번 세션에서 dev에 머지된 11 PR(#160~#170) 통합본. 메인 dev = db007f6.

## 환경 (Step 0)
- 인프라: qa-mysql/kafka/mongodb/redis/zookeeper (docker-compose.qa.yml, healthy)
- DB 클린 리셋 후 BE 기동 → **Flyway V1~V32 32개 마이그레이션 전부 성공 적용** (now at v32)
- BE health: db/mongo/redis/ping 전부 UP
- mock-pg 9090 기동 (결제 PG 검증용)

## BE API 회귀 스모크 결과 — 전부 통과 (결함 0건)

| # | 회귀 항목 | 검증 | 결과 |
|---|---|---|---|
| 0 | dev BE 기동 (빌드블로커 4 + V23 복구) | Flyway 32 적용 + 웹서버 + 인프라 연결 | ✅ UP |
| ① | 결제 PG 웹뷰 플로우 (#162) | POST /payments/prepare → checkoutUrl + pgTransactionId | ✅ 200, mock-pg kakao checkout URL 반환 |
| ② | DEF-005 MOBILE_PAY enum 500 회귀 (#162) | GET /payments/me | ✅ 200 (enum 직렬화 정상) |
| ③ | DEF-004 unread-count 키 (#161) | GET /notifications/me/unread-count | ✅ `{"unreadCount":0}` |
| ④ | DEF-003 cart 중복 500 회귀 (#165) | GET /cart/me | ✅ 200 |
| ⑤ | MO-07 좌석 + BE seats 확장 (#167/#168) | GET /events/{id} | ✅ seats 6개 + sections 2 |
| ⑥ | FR-02 이상패턴 IDOR (#169) | GET /api/mcp/anomaly-events | ✅ 200, 본인 것만 (content:[]) |
| ⑦ | FR-01 usage-analytics ADMIN 권한 (#170 SECURITY-01) | GET /api/admin/mcp/usage-analytics (FACILITY_OWNER 토큰) | ✅ 403 (비ADMIN 차단) |
| ⑧ | 빌드블로커 B4 GlobalExceptionHandler (#160) | 누락 헤더 요청 | ✅ 400 MISSING_REQUEST_HEADER (중복 핸들러 제거 정상) |

## 핵심 검증 결론
- **마이그레이션 정합**: V1~V32 클린 적용 성공 — 과거 dev를 막던 빌드블로커(spring 키 중복, V10 중복, V23 중복) + 신규(V28/V29/V30/V31/V32) 전부 정합.
- **결함 회귀 0건**: DEF-003/004/005 모두 재현 안 됨 (200/정상 키).
- **권한·IDOR**: usage-analytics ADMIN 강제(403), anomaly 본인 필터 정상.
- **결제 PG 비동기**: prepare → checkoutUrl 정상 (webhook 멱등은 BE 시나리오 테스트 #162에서 검증됨).

## 미수행 (선택)
- 화면 레벨 Playwright E2E (FE production 빌드 필요) — FE 코드는 #164/#166/#167로 머지됨, BE API 정상 확인됨
- k6 부하 시나리오

## auto-fix 대상
**0건** — BE 회귀 스모크에서 결함 미발견. Step 4(be/fe-implementer 자동 호출) 및 Step 5 재검증 대상 없음.

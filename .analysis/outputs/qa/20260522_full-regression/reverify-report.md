# 재검증 리포트 — /qa --full-regression (2026-05-22)

/ qa 파이프라인 Step 5(재검증) 산출물. fix·시드·spec 수정 후 회귀를 재실행한 결과.

## E2E 회귀 추이 (실측 — results.json 직접 파싱)

| 단계 | pass | fail | skip | 비고 |
|---|---|---|---|---|
| 1차 (FE 환경설정 오류 — `.env.local` 누락) | 60 | 20 | 10 | portal-dashboard 9건 거짓 fail (DEF-002 회고) |
| `.env.local` 보정 후 재실행 | 69 | 10 | 11 | portal 화면 9건 거짓 fail 해소 |
| 시드 주입 후 (`qa/e2e/fixtures/seed.sql`) | 74 | 10 | 6 | skip 5건 해제, 신규 회귀 0 |
| spec 결함 수정 후 (CARD enum 등) | 76 | 3 | 11 | E2E-06-R02는 BE 비멱등 결함으로 fail 전환 |

## login.spec.ts 풀 플로우 검증 (production 빌드)

`next start` 환경에서 4/4 통과 (mock 제거, 실제 fixture 로그인):

| 케이스 | 결과 | 검증 |
|---|---|---|
| E-01 | PASS | /login 폼 요소 가시성 |
| E-02 | PASS | 실제 로그인 → access_token 쿠키 → portal layout 인증 통과 → /portal |
| E-03 | PASS | 잘못된 자격증명 → BE 401 → 화면 에러 메시지 |
| E-04 | PASS | Enter 키 제출 → /portal |

`next dev`에서는 hydration 타이밍 비결정성으로 4건 중 3건 거짓 fail → production 빌드에서 4/4 통과로 확정.

## 발견·수정된 결함

| 결함 | fix PR | 판정 |
|---|---|---|
| DEF-001 Stock query derivation | #106 | 해결 — fix PR 생성 |
| DEF-002 portal SSR (be-client BACKEND_URL) | #108 | 코드 fix + `.env.local` 환경 보정 (1차 회귀 왜곡 원인) |
| DEF-003+004+007+009 ExceptionHandler/입력 검증 | #112 | 해결 — fix PR 생성 |
| DEF-005 bookings content 필드 | #110 | 해결 |
| DEF-006 payments status=PAID 500 | #111 | 해결 |
| DEF-008 unreadCount 필드명 | #109 | 해결 |
| DEF-010 부하 auth 헬퍼 불일치 | PR #104 | 해결 — headerAuth 전환 |
| DEF-011 zod v4 빌드 결함 (FE 배포 불가) | #118 | 해결 — production 빌드 복구 |
| /login 페이지 부재 | #116 | 해결 — 페이지 신규 구현 |

## 미해결 — 사람 검토 큐

| 케이스 | 사유 |
|---|---|
| E2E-06-R02 (goods-orders 멱등) | BE `CreateGoodsOrderUseCase` 주문 레벨 멱등성 부재 — 별도 BE 결함 티켓 필요 |
| E2E-05-R02 (ticket-orders 멱등) | BE 주문 레벨 멱등성 부재 + 좌석 영구 소진 |
| E2E-04-E04 (게이트웨이 5xx) | 결제 게이트웨이 stub 미구축 — application.yml 변경 + 재기동 필요 |
| E2E-07-R01 (대시보드 toLocaleString) | BE Redis 캐시 역직렬화 500 + 대시보드 FE/BE 필드명 불일치 |

위 4건은 별도 결함 티켓으로 추적 권장.

## 판정

- auto-fix 대상 결함(DEF-001~011 중 9건)은 모두 fix PR 생성 — fix 브랜치 단위 분리 완료
- 신규 회귀(직전 pass → 재검증 fail): E2E-06-R02 1건 — QA가 정상적으로 잡아낸 진짜 BE 결함
- 남은 미해결 4건은 사람 검토 큐로 이관 — reverify 통과 기준 미달이나, 원인이 모두 별도 BE/환경 결함이라 /qa Step 4 루프(최대 3회)로 해결 불가

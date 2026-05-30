# DEF-010 k6 부하 auth fixture 누락 — issueToken() 가 BE 실제 엔드포인트와 불일치하여 LOAD-02/03 SKIP

## 메타
- layer: INFRA
- severity: Major
- auto-fix-eligible: false
- source-scenario: LOAD-02, LOAD-03
- detected-at: 2026-05-22T02:17:00+09:00
- environment: docker-compose.qa.yml (commit `e810357`, branch `feat/qa-pipeline`)
- related-pr: none
- related-ticket: none

## 분류 근거
- `load-report.md` 의 SKIP 상세:
  > LOAD-02·LOAD-03 setup 단계에서 `issueToken()` 401 — `qa/load/k6/lib/auth.js#issueToken`
- 헬퍼 가정과 BE 실제 불일치 (load-report.md):
  | 항목 | 헬퍼 가정 | BE 실제 |
  |---|---|---|
  | 엔드포인트 | `POST /api/v1/auth/login` | `POST /auth/login` |
  | 자격 증명 필드 | `username` / `password` | `email` / `password` |
  | fixture 계정 | `qa-user` / `qa-pass` | 미존재 (시드 부재) |
- BE 측 결함이 아니라 **부하 측정 헬퍼와 BE 계약 사이 정합성 부재** + 시드 부재 → layer: INFRA (부하 인프라)
- 부하 측정 불가 = 회귀 추세 추적 차단 → Major (배포 게이트 영향 아님, 회피 가능)

## 재현 단계
1. `docker-compose -f qa/e2e/docker-compose.qa.yml up -d`
2. BE 기동
3. `k6 run qa/load/k6/ticket-seat-select-spike.js` 실행
4. setup 단계에서 `POST /api/v1/auth/login` → 401 → setup 실패 → 0 iteration
5. LOAD-03 (`booking-create-throughput.js`) 동일 양상

## 기대 동작
LOAD-02/03 가 정상 실행되어 ticket seat select / booking create 부하 시나리오의 RPS·p95·error rate가 측정됨.

## 실제 동작
양쪽 모두 0 iteration:
```
load-results/ticket-seat-select-spike/raw.log
load-results/booking-create-throughput/raw.log
```
응답: `401 Unauthorized {"status":401,"title":"Unauthorized","detail":"Authentication required"}`

## 영향 범위
- 영향 사용자: 없음 (운영 영향 없음. 부하 회귀 추세 추적 불가만 발생)
- 영향 화면/엔드포인트: 없음 — QA 인프라 측정 결함
- 데이터 영향: 없음

## 아티팩트
- [LOAD-02 raw.log](../load-results/ticket-seat-select-spike/raw.log)
- [LOAD-02 summary.json](../load-results/ticket-seat-select-spike/summary.json)
- [LOAD-02 threshold.txt](../load-results/ticket-seat-select-spike/threshold.txt)
- [LOAD-03 raw.log](../load-results/booking-create-throughput/raw.log)
- [LOAD-03 summary.json](../load-results/booking-create-throughput/summary.json)
- [LOAD-03 threshold.txt](../load-results/booking-create-throughput/threshold.txt)
- [load-report.md](../load-report.md) — auth fixture 상세 표 §2

## 의심 코드 경로

| 파일 | 역할 |
|---|---|
| `qa/load/k6/lib/auth.js` | `issueToken()` 헬퍼 — endpoint/payload 가정 부정확 |
| `qa/load/k6/ticket-seat-select-spike.js` | LOAD-02 시나리오 — auth.js 의존 |
| `qa/load/k6/booking-create-throughput.js` | LOAD-03 시나리오 — auth.js 의존 |
| `qa/load/seeds/booking-create.sql` (부재) | LOAD-03 시드 SQL 미존재 |
| `qa/e2e/docker-compose.qa.yml` | mysql 컨테이너 init-scripts에 부하 fixture 사용자 시드 마운트 부재 |
| `backend/src/main/kotlin/com/sportsapp/presentation/auth/LoginRequest.kt` (또는 동등) | BE 실제 시그니처 — `email` / `password` |

조치 옵션 (load-report.md §2 발췌, 본 router는 결정하지 않음):
1. `qa/load/k6/lib/auth.js#issueToken` endpoint 를 `/auth/login` 으로 정정 + payload 를 `{email, password}` 로 전환
2. BE seed 에 fixture 사용자 추가 (`qa@example.com` / `qa-pass`)
3. 또는 `X-User-Id` 헤더 기반 헬퍼 `headerAuth(userId)` 신규 추가 — 현재 `/bookings/**`, `/events/**` 은 permitAll + X-User-Id 기반이므로 Bearer 토큰 불필요. 부하 측정의 인증 노이즈 제거에 유리

## 자동 수정 지시
대상 에이전트: (없음 — 사람 처리)

`auto-fix-eligible: false` (layer: INFRA). 사람이 위 3가지 조치 중 어느 것을 적용할지 판단 필요:
- 운영 부하 시뮬레이션 충실도를 우선하면 옵션 1 + 2 (실제 인증 경로 사용)
- 측정 단순화를 우선하면 옵션 3 (X-User-Id 헤더 모델)

조치 후 LOAD-02/03 재실행하여 0 iteration이 아님을 확인.

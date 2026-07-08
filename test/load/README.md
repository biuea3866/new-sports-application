# MCP 부하 시험 스크립트

TEST-01 — MCP Server read/write tool 부하 시험 (k6).

**중요**: 실제 실행은 INFRA-02 머지 + staging 환경 준비 후 진행합니다.
이 PR은 스크립트 작성만 포함합니다.

---

## 합격 기준 (PRD v2.5)

| 시나리오 | 지표 | 목표 |
|---|---|---|
| read tool | P95 응답 시간 | < 800ms |
| read tool | 에러율 | < 0.5% |
| write tool | P95 응답 시간 (1차+2차 합산) | < 1.5s |
| write tool | 에러율 | < 1% |

k6 `thresholds`에 자동 판정 로직이 내장되어 있습니다. 임계 위반 시 `exit 99`로 CI를 실패시킵니다.

---

## 시나리오 매개변수

### mcp-read-load.js

| 항목 | 값 |
|---|---|
| 시나리오 ID | MCP-READ-LOAD-01 |
| 목표 VU | 200 |
| ramp-up | 0 → 100 VU (1분) |
| steady | 100 → 200 VU (8분) |
| ramp-down | 200 → 0 VU (1분) |
| 총 duration | 10분 |
| 목표 RPS | ~50 (tool call 기준) |
| 대상 tool | `getFacilities`, `getFacilityStats` |
| SSE 연결 P95 임계 | < 2000ms |

### mcp-write-load.js

| 항목 | 값 |
|---|---|
| 시나리오 ID | MCP-WRITE-LOAD-01 |
| 목표 VU | 100 |
| ramp-up | 0 → 50 VU (1분) |
| steady | 50 → 100 VU (8분) |
| ramp-down | 100 → 0 VU (1분) |
| 총 duration | 10분 |
| 목표 RPS | ~20 (1차+2차 각 10 RPS) |
| 대상 tool | `cancelBooking` (confirm flow 2단계) |
| confirm 1차 P95 임계 | < 800ms |
| confirm 2차 P95 임계 | < 1200ms |
| write flow 합산 P95 임계 | < 1500ms |

---

## 실행 방법

### 사전 조건

- k6 설치: https://grafana.com/docs/k6/latest/get-started/installation/
- 로컬 또는 staging 서버 기동 (`docker-compose up`)
- MCP 토큰 발급 또는 dev 환경 Admin 계정 준비

### 환경 변수

| 변수 | 필수 | 설명 |
|---|---|---|
| `QA_API_URL` | 권장 | 대상 서버 URL. 기본값 `http://localhost:8080` |
| `MCP_TOKEN` | 선택 | 발급된 MCP 토큰 평문. 없으면 `ADMIN_EMAIL`/`ADMIN_PASSWORD`로 자동 발급 |
| `ADMIN_EMAIL` | 선택 | MCP 토큰 발급용 관리자 이메일. 기본값 `load-test-admin@example.com` |
| `ADMIN_PASSWORD` | 선택 | 관리자 비밀번호. 기본값 `LoadTest1234!` |

**토큰 평문을 코드/커밋에 포함하지 마십시오.** 환경변수 또는 CI secrets로 주입하십시오.

### read tool 실행

```bash
# 로컬 dev 환경 (자동 토큰 발급)
QA_API_URL=http://localhost:8080 \
ADMIN_EMAIL=admin@example.com \
ADMIN_PASSWORD=Admin1234! \
k6 run test/load/mcp-read-load.js

# MCP 토큰 직접 주입
MCP_TOKEN=$MY_MCP_TOKEN \
QA_API_URL=http://localhost:8080 \
k6 run test/load/mcp-read-load.js

# 결과 JSON 내보내기
k6 run \
  --summary-export=test/load/results/read-summary.json \
  test/load/mcp-read-load.js
```

### write tool 실행

```bash
# 로컬 dev 환경 (자동 토큰 발급)
QA_API_URL=http://localhost:8080 \
ADMIN_EMAIL=admin@example.com \
ADMIN_PASSWORD=Admin1234! \
k6 run test/load/mcp-write-load.js

# 결과 JSON 내보내기
k6 run \
  --summary-export=test/load/results/write-summary.json \
  test/load/mcp-write-load.js
```

### 결과 해석

k6 종료 코드:
- `0` — 모든 threshold 통과 (합격)
- `99` — 1개 이상 threshold 위반 (불합격)

주요 메트릭 이름:

| 메트릭 | 설명 |
|---|---|
| `MCP-READ-LOAD-01_latency` | read tool 호출 응답 시간 |
| `MCP-READ-LOAD-01_failures` | read tool 에러율 |
| `MCP-READ-LOAD-01_sse_connect_latency` | SSE 연결 응답 시간 |
| `MCP-WRITE-LOAD-01_write_flow_latency` | write flow 전체 (1차+2차) 응답 시간 |
| `MCP-WRITE-LOAD-01_write_flow_failures` | write flow 에러율 |
| `MCP-WRITE-LOAD-01_confirm_step1_latency` | confirm 1차 호출 응답 시간 |
| `MCP-WRITE-LOAD-01_confirm_step2_latency` | confirm 2차 호출 응답 시간 |
| `MCP-WRITE-LOAD-01_confirm_tokens_issued` | 발급된 confirm 토큰 수 |

---

## 사전 시드 (선택)

실제 DB 데이터가 있어야 write flow 2차 호출이 성공합니다.
시드 SQL: `qa/load/seeds/mcp-write-load.sql` (INFRA-02 머지 후 작성 예정).

dev 환경에서 스크립트만 검증할 때는 2차 호출의 404/비즈니스 에러는 무시합니다.
1차 호출(confirm 토큰 발급) 성능이 핵심 측정 대상입니다.

---

## CI 연동

`.github/workflows/load-test.yml` 참조. 수동 trigger(`workflow_dispatch`)로만 실행합니다.
자동 실행(push/PR 트리거) 금지 — staging 환경 부하를 예기치 않게 유발합니다.

---

## 측정 노이즈 경고

로컬 docker-compose 환경의 측정값은 **절대치 비교용이 아닌 회귀 추세 추적용**입니다.
임계 위반은 결함 후보이며, 실제 결함은 staging 환경 부하로 재검증합니다.

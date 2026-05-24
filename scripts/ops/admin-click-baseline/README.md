# admin-click-baseline — BFF access log 집계 스크립트

## 목적

v1.1 FR-07 베타 측정 지표 **"어드민 클릭 -30% 감소"** 를 검증하기 위한 baseline 수집 인프라입니다.

| 단계 | 티켓 | 내용 |
|---|---|---|
| 사전 인프라 셋업 | **T16a (현재)** | 스크립트 작성 + dry-run 검증. 운영자 후보 풀 기준 |
| 실 baseline 수집 | T16b | T15 매칭 5팀 운영자 id 확보 후 진행 |

**T16b 진입 조건**: 게이트 #K (5/30) 통과 + T15 베타 5팀 매칭 완료 → 운영자 user_id 목록 확보.

## 파일 구조

```
scripts/ops/admin-click-baseline/
├── collect-baseline.sh       # 메인 집계 스크립트
├── README.md                 # 본 문서
├── fixtures/
│   └── sample-access.log     # dry-run 검증용 더미 로그 (10줄)
└── results/                  # 집계 결과 CSV 저장 위치 (git 비추적)
```

## 사용법

### 1. nginx_local 모드 (직접 grep)

로그 파일에 직접 접근 가능한 환경(서버 SSH, 로컬 테스트)에서 사용합니다.

```bash
# 환경변수 설정
export LOG_SOURCE=nginx_local
export NGINX_LOG_PATH=/var/log/nginx/access.log   # 기본값

# user_id 파일 준비 (1행 1개 또는 쉼표 구분)
echo "42,99,103" > /tmp/operator_ids.csv

# 실행 (2026-05-17 ~ 2026-05-23 1주 수집)
./collect-baseline.sh --users /tmp/operator_ids.csv --start 2026-05-17 --end 2026-05-23
```

### 2. datadog 모드 (Datadog Logs API)

K8s 환경에서 로그가 Datadog으로 수집되는 경우 사용합니다.

```bash
export LOG_SOURCE=datadog
export DATADOG_API_KEY=<secret>     # 평문 커밋 금지 — Vault / AWS SSM 에서 주입
export DATADOG_APP_KEY=<secret>     # 평문 커밋 금지
export DATADOG_SITE=datadoghq.com   # 기본값
export LOG_INDEX=main               # 기본값

./collect-baseline.sh --users /tmp/operator_ids.csv --start 2026-05-17 --end 2026-05-23
```

### 3. dry-run (fixtures 사용)

```bash
export LOG_SOURCE=nginx_local
export NGINX_LOG_PATH=fixtures/sample-access.log

echo "42" > /tmp/test_ids.csv
./collect-baseline.sh --users /tmp/test_ids.csv --start 2026-05-24 --end 2026-05-24
```

## 출력 형식

`results/baseline-{YYYYMMDD}-{YYYYMMDD}.csv` 파일이 생성됩니다.

```csv
user_id,date,total_clicks,distinct_paths
42,2026-05-24,4,3
99,2026-05-24,0,0
```

| 컬럼 | 설명 |
|---|---|
| `user_id` | 운영자 ID |
| `date` | 날짜 (YYYY-MM-DD) |
| `total_clicks` | 해당 날짜 `/api/admin/*` GET 요청 수 |
| `distinct_paths` | 고유 경로 수 (`/api/admin/facilities`, `/api/admin/users` 등) |

## 집계 범위

- 경로 패턴: `/api/admin/*` **만**
- HTTP 메서드: **GET 만** (POST/PUT/DELETE 제외)
- 운영자 식별: nginx access log의 `user_id={id}` 필드

## 환경변수 목록

| 변수 | 필수 | 기본값 | 설명 |
|---|---|---|---|
| `LOG_SOURCE` | ✅ | — | `nginx_local` 또는 `datadog` |
| `NGINX_LOG_PATH` | — | `/var/log/nginx/access.log` | nginx_local 모드 로그 경로 |
| `SPRING_LOG_PATH` | — | `backend/logs/access.log` | Spring 로그 경로 (예비) |
| `DATADOG_API_KEY` | datadog 모드 필수 | — | Datadog API 키 |
| `DATADOG_APP_KEY` | datadog 모드 필수 | — | Datadog App 키 |
| `DATADOG_SITE` | — | `datadoghq.com` | Datadog 사이트 |
| `LOG_INDEX` | — | `main` | Datadog 로그 인덱스 |

## secret 관리 정책

- `DATADOG_API_KEY`, `DATADOG_APP_KEY` 등 **secret은 환경변수로만** 받습니다.
- `.env` 파일, 스크립트 내부 하드코딩, git 커밋에 평문 포함 — **금지**.
- CI 환경: Vault 또는 AWS SSM Parameter Store에서 주입.
- 로컬 실행: `direnv` 또는 `export` 명령으로 쉘에 주입.

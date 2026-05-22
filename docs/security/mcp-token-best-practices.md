# MCP 토큰 발급 및 관리 모범 사례

> 작성일: 2026-05-23
> 대상: MCP 토큰을 발급·사용하는 운영자 및 개발자

---

## 1. 토큰 형식 이해

MCP 토큰은 다음 형식으로 발급됩니다.

```
mcp_<token_id>_<base64url_random>
```

| 구성요소 | 설명 |
|---|---|
| `mcp_` | 고정 접두사 |
| `<token_id>` | 시스템 내 토큰 ID (정수) |
| `<base64url_random>` | 32바이트 SecureRandom base64url 인코딩 |

예시: `mcp_42_X7k2mN9...`

**주의**: 이 형식의 문자열은 즉시 유출로 간주하고 SOP를 실행합니다.

---

## 2. 발급 원칙

### 2-1. Scope 최소화

필요한 권한만 발급합니다. 모든 scope를 일괄 부여하지 않습니다.

| 사용 목적 | 권장 scope |
|---|---|
| 시설 정보 조회만 | `read:facility` |
| 예약 현황 확인 | `read:booking:own` |
| 예약 취소 포함 | `read:booking:own`, `write:booking:own` |
| 상품 판매 분석 | `read:goods:own` |
| 알림 확인 | `read:notification:own` |

scope 형식: `{verb}:{domain}` 또는 `{verb}:{domain}:{qualifier}`

- verb: `read`, `write`
- qualifier: `own`(본인 소유 자원), `any`(전체 자원, 관리자 전용)

### 2-2. 만료일 설정 (필수 권고)

| 사용 유형 | 권장 만료 기간 |
|---|---|
| 임시 테스트 | 1~7일 |
| 주기적 업무 자동화 | 30일 (갱신 알림 설정) |
| 상시 운영 agent | 90일 (최대 권장) |
| CI/CD 파이프라인 | 최소 기간 (배포 주기 + 7일) |

> 무기한 토큰(`expires_at = null`)은 가능하지만 권고하지 않습니다.
> 무기한 토큰은 유출 시 영구적 피해가 발생합니다.

### 2-3. 토큰 명칭 규약

토큰 이름은 사용 목적을 명확히 기술합니다.

```
좋은 예시:
- "2026-Q2 시설 현황 조회 agent"
- "예약 취소 자동화 bot (만료: 2026-08-01)"
- "Claude Desktop 개인 사용"

나쁜 예시:
- "test"
- "my token"
- "token1"
```

---

## 3. 토큰 보관 규칙

### 금지 항목

- 소스코드 또는 설정 파일에 평문 저장 (`application.yml`, `.env` 등)
- Git 저장소에 커밋 (공개·비공개 무관)
- Slack·Teams·이메일 등 메신저로 전송
- 화면 공유 중 터미널에 출력
- 로그 파일에 출력

### 권장 보관 방법

| 환경 | 방법 |
|---|---|
| 로컬 개발 | OS 키체인 또는 `.env.local` (`.gitignore`에 등록 필수) |
| CI/CD | GitHub Actions Secret, GitLab CI Variable 등 시크릿 관리 기능 |
| 서버 배포 | 환경 변수 주입 (Kubernetes Secret, Vault 등) |
| AI Desktop 앱 | 앱 내장 시크릿 저장소 (예: Claude Desktop config) |

### 유출 탐지 자동화

Git 저장소에 pre-commit hook을 설정하여 `mcp_` 패턴을 자동 차단합니다.

```bash
# .git/hooks/pre-commit (또는 pre-commit 프레임워크 사용)
if git diff --cached | grep -E 'mcp_[0-9]+_[A-Za-z0-9_-]{20,}'; then
  echo "ERROR: MCP 토큰이 커밋에 포함되어 있습니다. 즉시 제거하세요."
  exit 1
fi
```

---

## 4. 토큰 사용 시 주의사항

### 4-1. 요청 헤더 전달

```
Authorization: Bearer mcp_<id>_<random>
```

HTTPS를 반드시 사용합니다. HTTP에서는 절대 사용하지 않습니다.

### 4-2. 호출 로그 확인

토큰을 사용하는 시스템에서 이상한 호출이 없는지 정기적으로 확인합니다.

```bash
# Admin API로 최근 100건 조회
curl "https://<API_HOST>/api/admin/mcp/audit-logs?tokenId=<id>&limit=100" \
  -H "Authorization: Bearer <admin_jwt>"
```

확인 항목:
- 본인이 실행한 호출만 있는지
- 예상치 못한 IP에서의 호출이 없는지
- `status_code = 403` 호출이 없는지 (scope 위반 시도)

### 4-3. 비정상 알림 대응

시스템에서 비정상 패턴 알림을 수신하면 즉시 확인합니다.

- 알림 조건: 직전 1시간 호출 수가 7일 베이스라인 일평균의 2배를 초과
- 알림 채널: `[알림 채널]` ← v1.1에서 기입
- 즉각 조치: 본인의 정상 사용이 아니면 토큰 즉시 폐기 후 SOP 실행

---

## 5. 토큰 폐기 절차

### 정기 폐기 (만료 전 능동적 폐기)

다음 상황에서는 만료 전이라도 토큰을 폐기합니다.

- 담당자 퇴직 또는 업무 변경
- 해당 토큰을 사용하는 시스템 폐기
- 발급 목적이 완료된 경우

```
경로: 포털 → Admin → MCP 토큰 관리 → 대상 토큰 → 폐기
```

### 긴급 폐기

유출 의심 시 즉시 폐기하고 [MCP 토큰 유출 SOP](./mcp-token-leak-sop.md)를 실행합니다.

---

## 6. 비인가 접근 방지 체크리스트

새 토큰 발급 전 다음을 확인합니다.

- [ ] 목적에 맞는 최소 scope만 선택했는가
- [ ] 만료일을 설정했는가
- [ ] 토큰 이름에 사용 목적을 명확히 기술했는가
- [ ] 보관 방법을 결정했는가 (키체인 / 시크릿 관리 도구)
- [ ] pre-commit hook이 설정되어 있는가 (코드베이스 사용 시)

---

## 7. 관련 문서

| 문서 | 설명 |
|---|---|
| [MCP 토큰 유출 SOP](./mcp-token-leak-sop.md) | 유출 발생 시 즉각 조치 절차 |
| [인시던트 보고서 템플릿](./mcp-incident-report-template.md) | 사후 보고서 양식 |

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-23 | v1.0 초안 — DOCS-01 구현 | Claude Sonnet 4.6 |

# MCP 토큰 유출 SOP (Standard Operating Procedure)

> 버전: v1.0 초안 — Legal 검토 후 v1.1 보완 예정 (§4 대외 통지 섹션)
> 작성일: 2026-05-23
> 관련 이슈: Open Issue #D (Legal), #L (대외 통지 정책)

---

## 1. 목적 / 범위

### 목적

MCP(Model Context Protocol) 토큰이 외부에 노출되었을 때 피해를 최소화하고, 원인을 추적하고, 재발을 방지하는 절차를 명시합니다.

### 적용 범위

다음 상황에 모두 적용됩니다.

| 유출 유형 | 예시 |
|---|---|
| 평문 토큰 노출 | `mcp_<id>_<random>` 형식의 토큰이 로그·코드·채팅에 노출됨 |
| 토큰 해시 노출 | `mcp_tokens.token_hash` 컬럼 덤프 유출 |
| 비인가 접근 징후 | 알 수 없는 IP·User-Agent에서 토큰 호출 확인 |
| 내부 오용 | 권한 범위(scope)를 초과한 tool 호출 |

### 토큰 형식 참조

```
mcp_<token_id>_<base64url_random_32bytes>
```

- 예: `mcp_42_X7k2...` (평문 토큰. 절대 공유 금지)
- 시스템에는 bcrypt 해시만 저장됨 (`mcp_tokens.token_hash`)

---

## 2. 유출 발견 시 즉각 조치 (5분 이내)

### 2-A. 운영자 본인이 발견한 경우

1. **즉시 토큰 폐기** — 포털에서 직접 처리합니다.

   ```
   경로: [포털 URL]/admin/mcp/tokens
   조작: 대상 토큰 행 → "폐기(Revoke)" 클릭
   ```

   API 직접 호출 시:

   ```bash
   curl -X DELETE https://<API_HOST>/api/admin/mcp/tokens/<token_id> \
     -H "Authorization: Bearer <admin_jwt>"
   ```

2. **폐기 확인** — 토큰 상태가 `REVOKED`로 전환되었는지 확인합니다.

   ```bash
   curl https://<API_HOST>/api/admin/mcp/tokens \
     -H "Authorization: Bearer <admin_jwt>"
   # 응답에서 해당 tokenId의 status == "REVOKED" 확인
   ```

3. **보안 담당자에게 즉시 보고** — 이메일 또는 메신저로 통보합니다.

   ```
   보안 담당자: [보안 담당자 이메일]  ← v1.1에서 실제 연락처 기입
   제목: [긴급] MCP 토큰 유출 의심 — tokenId=<id>
   내용: 발견 경위, 유출 추정 경로, 이미 취한 조치
   ```

### 2-B. 관리자(Admin)가 발견한 경우

1. **DB 직접 폐기** (포털 접근이 불가하거나 긴급한 경우):

   ```sql
   -- Read-only 확인 먼저
   SELECT id, user_id, status, last_used_at FROM mcp_tokens WHERE id = <token_id>;

   -- 폐기
   UPDATE mcp_tokens SET status = 'REVOKED', updated_at = NOW() WHERE id = <token_id>;
   ```

   > 주의: DB 직접 수정 후 운영자에게 반드시 통지합니다. 이력이 `mcp_audit_logs`에 자동 기록되지 않으므로 인시던트 보고서에 수동 기록합니다.

2. **운영자 통지** — 해당 토큰 소유자(`mcp_tokens.user_id`)에게 직접 연락합니다.

   ```
   통지 내용:
   - 토큰 ID 및 이름
   - 폐기 일시
   - 폐기 사유 요약
   - 신규 토큰 발급 안내
   ```

---

## 3. 유출 영향도 평가 (30분 이내)

### 3-1. 감사 로그 조회

`mcp_audit_logs` 테이블에서 해당 토큰의 전체 호출 이력을 조회합니다.

```sql
SELECT
    id,
    token_id,
    user_id,
    tool_name,
    params_masked,
    status_code,
    latency_ms,
    ip_addr,
    client_user_agent,
    called_at
FROM mcp_audit_logs
WHERE token_id = <token_id>
  AND called_at >= <유출_추정_시각>
ORDER BY called_at ASC;
```

또는 Admin API를 사용합니다:

```bash
curl "https://<API_HOST>/api/admin/mcp/audit-logs?tokenId=<token_id>&from=<ISO8601>" \
  -H "Authorization: Bearer <admin_jwt>"
```

### 3-2. 비정상 패턴 확인

다음 징후가 있는지 확인합니다.

| 확인 항목 | SQL / 방법 |
|---|---|
| 평소와 다른 IP 대역 | `SELECT DISTINCT ip_addr FROM mcp_audit_logs WHERE token_id = <id>` |
| 평소와 다른 User-Agent | `SELECT DISTINCT client_user_agent FROM mcp_audit_logs WHERE token_id = <id>` |
| 비정상 호출 급증 | `McpAnomalyDetectedEvent` 발행 내역 확인 (BE-08b 알림 로그) |
| scope 초과 시도 | `status_code = 403` 행 확인 |
| 심야·새벽 호출 | `HOUR(called_at) NOT IN (...)` 조건으로 필터 |

**BE-08b 비정상 알림 확인:**

```
로그 위치: application 로그 (grep: "McpAnomalyDetectedEvent")
알림 조건: 직전 1시간 호출 수 > 7일 베이스라인 일평균 × 2
```

### 3-3. PII 노출 가능성 분석

`params_masked` 컬럼을 분석합니다.

- `[MASKED]` 또는 `***` 형태로 마스킹된 필드 → PiiMasker가 처리한 것 (원본 미저장)
- 마스킹 되지 않은 일반 파라미터 → 조회 범위가 어느 리소스인지 확인

```sql
SELECT tool_name, params_masked, status_code
FROM mcp_audit_logs
WHERE token_id = <token_id>
  AND status_code = 200
  AND called_at >= <유출_추정_시각>;
```

PII 노출 판정 기준:

| 상황 | 판정 |
|---|---|
| params_masked에 마스킹 표시(`[MASKED]`) 없음 + 조회된 데이터에 개인정보 포함 | **PII 노출 가능** |
| 모든 PII 필드가 마스킹됨 | PII 노출 가능성 낮음 |
| write tool(cancelBooking 등) 성공 호출 존재 | **데이터 변경 발생** |

---

## 4. 대외 통지

> 이 섹션의 구체적 문구·기준·기관명은 **Legal 검토 후 v1.1에서 확정**됩니다.
> 현재는 절차 뼈대만 명시합니다.

### 4-1. 영향받은 사용자 통지 (PII 노출 시)

- 적용 조건: §3-3에서 PII 노출 가능으로 판정된 경우
- 통지 방법: 이메일 (시스템 발송) + 앱 내 알림
- 통지 기한: [v1.1에서 Legal이 결정] ← 예: 유출 인지 후 72시간 이내
- 통지 내용:
  - 유출된 데이터 범위 (어떤 정보가 노출되었는지)
  - 발생 일시
  - 이미 취한 조치
  - 사용자가 취해야 할 행동
  - 문의 연락처: `[고객지원 이메일]` ← v1.1에서 기입

### 4-2. 규제 기관 신고

- 적용 조건: [v1.1에서 Legal이 결정] ← 예: PII 1,000명 이상 노출 등
- 신고 대상: `[규제 기관명]` ← v1.1에서 기입
- 신고 기한: `[v1.1에서 Legal이 결정]`
- 신고 담당자: `[Legal 연락처]` ← v1.1에서 기입

---

## 5. 재발 방지

### 5-1. Scope 최소화 정책 검토

유출된 토큰의 scope를 확인하고, 실제 업무에 불필요한 권한이 있었는지 점검합니다.

```sql
SELECT mts.permission_id, p.name
FROM mcp_token_scopes mts
JOIN permissions p ON p.id = mts.permission_id
WHERE mts.token_id = <token_id>;
```

권장 정책:

| 사용 목적 | 권장 scope |
|---|---|
| 조회 전용 AI agent | `read:facility`, `read:booking:own` 등 read만 |
| 예약 취소 가능 agent | `write:booking:own` 추가 |
| 전체 관리 | scope 최소화 원칙 — 필요한 것만 발급 |

### 5-2. 비정상 패턴 임계값 조정

`McpAnomalyDetector`의 탐지 기준을 검토합니다.

```
현재 기준: 직전 1시간 호출 수 > 7일 베이스라인 일평균 × 2
파일 위치: domain/mcp/McpAnomalyDetector.kt
```

이번 사건에서 임계값이 너무 높거나 낮았다면 `ANOMALY_MULTIPLIER` 상수를 조정합니다.

### 5-3. 토큰 발급 정책 강화

현재 토큰 발급 정책을 검토합니다.

| 정책 항목 | 현재 기본값 | 권고 |
|---|---|---|
| 만료 기간 (`expires_at`) | 발급 시 선택 (무기한 허용) | 업무용은 최대 90일 권장 |
| non_interactive 설정 | 발급 시 선택 | CI/CD 전용 토큰은 반드시 true |
| pii_unmask_granted | 기본 false | 최소화 원칙 유지 |

---

## 6. 사후 보고서

**제출 기한**: 인시던트 종결 후 24시간 이내

사후 보고서 작성에는 다음 템플릿을 사용합니다:

- [인시던트 보고서 템플릿](./mcp-incident-report-template.md)

보고서에는 다음을 반드시 포함합니다.

| 항목 | 내용 |
|---|---|
| 타임라인 | 유출 추정 시각 ~ 폐기 완료까지 각 조치 시각 |
| 영향 범위 | 노출된 토큰 수, 노출된 사용자 수, PII 노출 여부 |
| 근본 원인 | 왜 토큰이 유출되었는가 |
| 재발 방지 조치 | 이미 완료된 조치 / 진행 중인 조치 |
| 미완료 항목 | 후속 ticket 또는 Legal 결정 대기 항목 |

보고서는 다음 경로에 저장합니다:

```
docs/security/incidents/<YYYYMMDD>-mcp-token-<token_id>.md
```

---

## 연락처 (v1.1에서 실제 값 기입)

| 역할 | 연락처 |
|---|---|
| 보안 담당자 | `[보안 담당자 이메일]` |
| Legal 담당자 | `[Legal 연락처]` |
| 고객지원 팀 | `[고객지원 이메일]` |
| 인프라 On-call | `[인프라 담당자 연락처]` |

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-23 | v1.0 초안 — DOCS-01 구현, Legal 검토 전 | Claude Sonnet 4.6 |

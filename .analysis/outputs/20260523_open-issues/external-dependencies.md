# 외부 의존 Open Issue 컨택 포인트 — PRD 3종 통합

3개 PRD (v1.1 / v2 / 대시보드) 의 Open Issue 중 **PM 본인이 결정 불가능한 외부 의존 17건** 을 담당자·게이트·진입 milestone 기준으로 정리합니다.

## 전체 외부 의존 17건 — 담당자별 분류

| 담당 | 건수 | Open Issue |
|---|---|---|
| **PG (Toss/PortOne/KakaoPay)** | 2 | v1.1 #1, v1.1 #7 |
| **DevOps** | 5 | v1.1 #2, v1.1 #5, v1.1 #9, v1.1 #11, v2 #5 |
| **Legal + 보안** | 5 | v1.1 #3, v2 #1, v2 #16, v2 #17, 대시보드 #3, 대시보드 #9 |
| **TPM (외부 PoC)** | 2 | v1.1 #K 게이트, v2 #11 |
| **운영팀 + SRE (데이터 수집 후 결정)** | 2 | 대시보드 #10, 대시보드 #16 |
| **DevOps + QA (PoC)** | 1 | 대시보드 #7 |

---

## C-1. PG 관련 — Toss / PortOne / KakaoPay (2건)

### v1.1 #1 — PG 사 선정
- **담당**: PM + DevOps
- **결정 항목**: Toss Payments / PortOne (구 IMP) / KakaoPay 중 1사
- **결정 기준**: 수수료율 / sandbox API 안정성 / 가맹점 등록 절차 / 환불 API 우선 지원
- **컨택**: PM 측 영업 미팅 + DevOps 측 sandbox 테스트
- **기한**: v1.1 #7 (PG sandbox 확보) 과 동시 — 2026-06-15 ~ 2026-07-15 사이
- **차단**: v1.1.1 milestone (FR-01 실 PG 어댑터 통합)
- **결정 후 액션**: PRD v1.1 FR-01 구현 어댑터 클래스명 확정 (`TossPaymentRefundGatewayImpl` 등)

### v1.1 #7 (PG #7 별도) — PG sandbox 확보
- **담당**: DevOps
- **결정 항목**: sandbox API key / 환불 API endpoint / webhook URL 등록 / 테스트 카드번호
- **컨택**: 선정된 PG 사의 개발자 포털 → 가맹점 신청 → sandbox 발급
- **기한**: PG #1 결정 후 14일 이내 (행정 절차 일반 7~14일)
- **차단**: v1.1.1 (FR-01 staging E2E 4 시나리오 통과)

---

## C-2. DevOps (5건)

### v1.1 #2 — MCP 도메인 prod URL 확정
- **담당**: DevOps
- **결정 항목**: `mcp-api.sportsapp.com` vs `api.sportsapp.com/mcp/*` vs 기타
- **컨택**: 인프라팀장 + DNS 관리자 + 보안 (인증서 발급)
- **기한**: **2026-05-27** (게이트 #B)
- **차단**: v1.1.0-a (FR-02 MCP 도메인 분리)
- **결정 후 액션**: nginx config 작성 + Route53 / Cloudflare DNS 등록 + Let's Encrypt 인증서

### v1.1 #5 — k6 staging DB/Redis 스펙
- **담당**: DevOps + QA
- **결정 항목**: 운영 동등 vs 축소 — 축소 시 합격 기준을 상대값으로 재정의 필요
- **컨택**: 인프라팀장 + QA 리드
- **기한**: INFRA-02 (nginx SSE timeout + WAF rate limit) 적용 시
- **차단**: v1.1.2 (FR-05 k6 부하 시험 합격)

### v1.1 #9 — PG 시크릿 관리 방법
- **담당**: DevOps + 보안
- **결정 항목**: AWS Secrets Manager / HashiCorp Vault / Spring Cloud Config / K8s Secret 중 1개
- **컨택**: 인프라팀장 + 보안팀장
- **기한**: FR-01 착수 **14일 전** (PG #1 결정 직후)
- **차단**: v1.1.1 (FR-01 — application-prod.yml 시크릿 주입 방식 결정)
- **결정 후 액션**: 시크릿 인프라 프로비저닝 + IAM 역할 부여

### v1.1 #11 — FR-05 staging DB/Redis/Tomcat 스펙
- **담당**: DevOps + QA
- **결정 항목**: 운영 동등 vs 축소
- **기한**: INFRA-02 적용 **14일 전**
- **차단**: v1.1.2 (k6 부하 시험)
- **#5 와 통합 결정 가능**

### v2 #5 — Scheduled job 실행 엔진
- **담당**: BE + DevOps
- **결정 항목**: Spring `@Scheduled` + ShedLock (단순) vs Quartz cluster mode (HA) — K8s replica 수 확인 후
- **컨택**: 인프라팀장 (현재 K8s replica 수 확인 필요) + BE 리드
- **기한**: **2026-07-28** (v2.0.2 진입 14일 전)
- **차단**: v2.0.2 (FR-05 Scheduled tool)
- **참고**: replica 1개 = ShedLock 불필요 / replica 2개 이상 = ShedLock 또는 Quartz 필수

---

## C-3. Legal + 보안 (6건)

### v1.1 #3 — 토큰 유출 통지 기한 + 규제 신고
- **담당**: Legal
- **결정 항목**: 24h vs 72h 통지 / 규제 기관 신고 적용 범위
- **컨택**: 사내 Legal + 외부 자문 (필요 시)
- **기한**: **2026-06-03** (게이트 #C/#D/#L)
- **차단**: v1.1.0-b (FR-03 SOP placeholder 9곳)
- **결정 후 액션**: `docs/security/mcp-token-leak-sop.md` placeholder 9곳 실 값 기입

### v2 #1 — 자동 환불 정책 (소비자보호법)
- **담당**: Legal + PM
- **결정 항목**: 사용자 사전 동의 필요 여부 / 자동 통지 의무 / 환불 가능 범위
- **컨택**: Legal + PM
- **기한**: **2026-06-15** (v2 착수 30일 전)
- **차단**: v2.0.0 (FR-01 + #14 `automation:bypass-confirm:refund` scope)
- **참고**: v2 #14 = `:refund` 별도 scope 분리로 결정됐으므로 환불 외 4개 tool 은 v2.0.0 진입 가능

### v2 #16 — webhook SSRF 차단 IP 범위
- **담당**: 보안팀
- **결정 항목**: 차단 IP 범위 최종 확정 (private network / cloud metadata / loopback / link-local + 추가)
- **컨택**: 보안팀장 + 인프라
- **기한**: **2026-07-14** (v2.0.1 진입 14일 전)
- **차단**: v2.0.1 (FR-02 Webhook tool)

### v2 #17 — `mcp_scheduled_jobs.params_json` 저장 정책
- **담당**: 보안 + BE
- **결정 항목**: 평문 vs PiiMasker 적용 vs AES-256 컬럼 암호화
- **컨택**: 보안팀장 + BE 리드
- **기한**: **2026-07-28** (v2.0.2 진입 14일 전)
- **차단**: v2.0.2 (FR-05 Scheduled tool — V27 migration)

### 대시보드 #3 — anomaly event 보관 기간 180일
- **담당**: Legal
- **결정 항목**: 180일 보관 GDPR / 개인정보보호법 충족 여부 + 운영자 본인 삭제 요청 시 처리
- **컨택**: Legal
- **기한**: **2026-06-30** (v1.0.0 진입 30일 전)
- **차단**: 대시보드 v1.1.0 (FR-02 anomaly 영속화)
- **#9 와 통합 결정 가능**

### 대시보드 #9 — 데이터 보존 정책 (GDPR / 개인정보보호법)
- **담당**: Legal
- **결정 항목**: anomaly / 알림 / audit log 각 보관 기간 + 본인 삭제 요청 처리 절차
- **컨택**: Legal + 보안
- **기한**: **2026-06-30** (v1.0.0 진입 30일 전)
- **차단**: 대시보드 v1.0.0 (FR-01 + audit log 조회)
- **#3 과 통합 결정 가능**

---

## C-4. TPM 외부 PoC (2건)

### v1.1 게이트 #K — 5클라이언트 PoC 보고
- **담당**: TPM
- **결정 항목**: Claude Desktop / ChatGPT Desktop / Cursor / Continue.dev / n8n 각 confirm UX + PII 마스킹 + 에러 처리 매트릭스 + 합격 기준
- **컨택**: TPM
- **기한**: **2026-05-30**
- **차단**: v1.1.3 베타 진입
- **v1.1 #10 (n8n 포함 여부) 함께 결정** — 미지원 시 Zed / Windsurf 자동 fallback

### v2 #11 — n8n MCP 연동 검증
- **담당**: TPM
- **결정 항목**: n8n community node 가용성 + HTTP node 검증 PoC
- **컨택**: TPM (v1.1 게이트 #K 결과 통보 시 함께)
- **기한**: **v1.1 #K 결과 통보 시 (2026-05-30 직후)**
- **차단**: v2.0.3 (FR-03 n8n 워크플로우 갤러리)

---

## C-5. 운영팀 + SRE — 데이터 수집 후 결정 (2건)

### 대시보드 #10 — `mcp_audit_logs` 90일 row 수 측정
- **담당**: 운영팀 + SRE
- **결정 항목**: 1 운영자당 평균 / 최대 / 95퍼센타일 row 수 → 인덱스/파티셔닝 필요성 판단
- **현재 추정** (코드베이스 조사): 베타 5팀 × 5운영자 × 90일 × 일평균 1000회 = **2.25M row** — MySQL 단일 테이블 충분
- **기한**: **2026-07-14** (v1.0.0 진입 14일 전 — 베타 운영 1주 후 재측정)
- **차단**: 대시보드 v1.0.0 (FR-01 MCP 사용 분석 — 집계 인프라 필요성)

### 대시보드 #16 — P95 latency 집계 계산 방법
- **담당**: BE + DBA
- **결정 항목**: MySQL 8.0 `PERCENTILE_CONT` vs application 정렬 vs 사전 batch 집계
- **컨택**: BE 리드 + DBA
- **기한**: **2026-08-04** (v1.2.0 진입 30일 전, 대시보드 #7 PoC 와 동시)
- **차단**: 대시보드 v1.2.0 (FR-05 KPI 위젯 P95 표시)

---

## C-6. DevOps + QA — 집계 인프라 PoC (1건)

### 대시보드 #7 — 집계 인프라
- **담당**: BE + DevOps + QA
- **결정 항목**: Materialized View vs ClickHouse vs Application batch — 옵션별 PoC + 부하 시험 결과 비교
- **컨택**: BE 리드 + 인프라팀장 + QA 리드
- **기한**: **2026-08-04** (v1.2.0 진입 30일 전)
- **차단**: 대시보드 v1.2.0 (FR-05 KPI 위젯 집계 성능)
- **PoC 항목**:
  - Option A: MySQL Materialized View (실시간성 vs 갱신 비용)
  - Option B: ClickHouse (별도 인프라 운영 비용)
  - Option C: Application batch (5분 주기, Redis 캐싱)

---

## 종합 진입 차단 매트릭스

milestone 별로 어떤 외부 의존이 차단하는지:

| Milestone | 차단 Open Issue | 가장 빠른 진입 시점 |
|---|---|---|
| v1.1.0-a (MCP 도메인) | v1.1 #2 | 2026-05-27 + 7일 = **2026-06-03** |
| v1.1.0-b (SOP) | v1.1 #3 | 2026-06-03 + 7일 = **2026-06-10** |
| v1.1.0-c (V25 ticket_order_id) | (PM 결정 완료) | **즉시 진입 가능** |
| v1.1.1 (실 PG) | v1.1 #1 + #7 + #9 | 2026-07-15 + 21일 = **2026-08-05** |
| v1.1.2 (부하 시험) | v1.1 #5/#11 | v1.1.1 + 운영 1주 + 14일 = **2026-08-25** |
| v1.1.3 (베타) | 게이트 #K + #10 | **2026-05-30 ~ 베타 진입 즉시 (v1.1.2 후)** |
| v2.0.0 (자동화 기본) | v2 #1 + #16 | 2026-07-14 + 21일 = **2026-08-04** |
| v2.0.1 (Webhook) | v2 #16 | 2026-07-14 + 21일 = **2026-08-04** |
| v2.0.2 (Scheduled) | v2 #5 + #17 | 2026-07-28 + 14일 = **2026-08-11** |
| v2.0.3 (n8n 갤러리) | v2 #11 + v1.1 INFRA-02 | v1.1.2 후 14일 = **2026-09-08** |
| 대시보드 v1.0.0 | 대시보드 #9 (Legal) + #10 (90일 측정) | 2026-07-14 + 21일 = **2026-08-04** |
| 대시보드 v1.1.0 (anomaly 영속화) | 대시보드 #3 (Legal) | v1.0.0 + 21일 = **2026-08-25** |
| 대시보드 v1.2.0 (KPI 위젯) | 대시보드 #7 + #16 | 2026-08-04 + 30일 = **2026-09-03** |

---

## 게이트 통과 우선순위 (PM 액션 목록)

가장 많은 milestone 을 unlock 하는 순서:

1. **2026-05-27 — v1.1 #2 (MCP 도메인)** — DevOps. v1.1.0-a 즉시 unlock
2. **2026-05-30 — 게이트 #K (TPM PoC)** — v1.1.3 베타 진입 차단 해제
3. **2026-06-03 — v1.1 #3 + Legal #C/#D/#L** — v1.1.0-b unlock
4. **2026-06-15 — v2 #1 (Legal 자동 환불)** — v2.0.0 unlock
5. **2026-06-30 — 대시보드 #3 + #9 (Legal 데이터 보존)** — 대시보드 v1.0.0 unlock
6. **2026-07-14 — v2 #16 (SSRF) + 대시보드 #10 (row 측정)**
7. **2026-07-15 — v1.1 #1 + #7 + #9 (PG 통합)** — v1.1.1 unlock
8. **2026-07-28 — v2 #5 + #17 (Scheduled 인프라)** — v2.0.2 unlock
9. **2026-08-04 — 대시보드 #7 (집계 인프라 PoC)** — v1.2.0 unlock

---

## Slack/Jira 메시지 템플릿

PM 이 각 담당자에게 보낼 수 있는 메시지 초안:

### DevOps 게이트 #B (2026-05-27)
```
@devops-lead

B2B MCP Server v1.1 진입 차단 게이트입니다. 2026-05-27 까지 결정 필요.

질문: MCP 노출 prod 도메인 URL 확정
- 옵션 A: mcp-api.sportsapp.com (서브도메인)
- 옵션 B: api.sportsapp.com/mcp/* (path prefix)
- 옵션 C: 기타 (제안 필요)

영향: v1.1.0-a 마일스톤 (FR-02 도메인 분리)
참고: .analysis/outputs/20260523_mcp-server-v1.1/prd.md
```

### Legal 게이트 #C/#D/#L (2026-06-03)
```
@legal-team

B2B MCP Server v1.1 + v2 진입 차단 게이트입니다. 2026-06-03 까지 결정 필요.

질문 3건:
1. 토큰 유출 시 사용자 통지 기한: 24h vs 72h
2. 규제 기관 신고 적용 범위 (개인정보 영향 임계값)
3. 보안 담당자 / Legal 연락처 9곳 placeholder 실 값

영향: v1.1.0-b 마일스톤 (FR-03 SOP)
참고: docs/security/mcp-token-leak-sop.md (placeholder 9곳)
```

### TPM 게이트 #K (2026-05-30)
```
@tpm

B2B MCP Server v1.1 베타 진입 차단 게이트입니다. 2026-05-30 까지 PoC 결과 필요.

PoC 대상: Claude Desktop / ChatGPT Desktop / Cursor / Continue.dev / n8n
검증 항목:
- confirm UX 표시 + 응답 처리
- PII 마스킹 (pii:unmask scope 미보유)
- 에러 처리 (401/403/RESPONSE_TOO_LARGE)

n8n 미지원 시 대체: Zed / Windsurf
영향: v1.1.3 베타 + v2.0.3 n8n 갤러리
```

---

## Open Issue 상태 요약 (3 PRD 통합)

| PRD | 전체 | 해소 (코드/PM) | 외부 의존 | 진행률 |
|---|---|---|---|---|
| v1.1 | 13 | 7 (코드 0 + PM 7) | 6 (#1/#2/#3/#5/#7/#9/#11 + 게이트 #K) | 54% |
| v2 | 18 | 12 (코드 1 + PM 11) | 6 (#1/#5/#11/#16/#17 + 게이트 #K) | 67% |
| 대시보드 | 23 | 17 (코드 4 + PM 13) | 6 (#3/#7/#9/#10/#16 + 운영팀장 메모) | 74% |
| **합계** | **54** | **36** | **17 (중복 제거 후)** | **67%** |

---

## 다음 단계

1. **2026-05-27** 게이트 #B 통과 → v1.1.0-a 즉시 진입 (TPM 분해 → `/feature` 진입)
2. **2026-05-30** 게이트 #K 통과 → 5클라이언트 PoC 보고 + v1.1 #10 결정
3. **외부 의존 메시지 발송** — 위 Slack 템플릿 PM 측 발송 → 회신 대기
4. **PM 결정 완료된 항목** → v1.1 / v2 / 대시보드 PRD body 의 FR/제약/마일스톤 본문에 실 값 반영 (현재 Open Issue 표는 갱신 완료)

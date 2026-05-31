# AI 운영 어시스턴트 (AI Ops Assistant) PRD

> 작성일: 2026-05-31
> 작성자: biuea3866@gmail.com
> 소스: C2 비즈니스 확장 아이디어 — MCP 서버 + 이상탐지 자산 레버리지
> 관련 PRD: [MCP v2 운영자 워크플로우 자동화](../20260523_mcp-server-v2-automation/prd.md), [B2B 운영 인사이트 대시보드](../20260523_b2b-insights-dashboard/prd.md)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/6 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-01 | 운영 신호 통합 수집 (Signal Inbox) | ⬜ 대기 | — | — | operator_inbox 확장 |
| FR-02 | AI 진단 — 원인분석 + 권장 조치안 생성 | ⬜ 대기 | — | — | FR-01 선행 |
| FR-03 | 권장 조치 승인 워크플로우 (1-click) | ⬜ 대기 | — | — | FR-02 선행, MCP confirm flow 재사용 |
| FR-04 | 자동 실행 정책 (위험도별 게이트) | ⬜ 대기 | — | — | FR-03 선행, Legal 게이트 |
| FR-05 | 조치 결과 추적 + false positive 학습 | ⬜ 대기 | — | — | FR-03 선행 |
| FR-06 | 안전장치 (audit 마킹·dry-run·롤백) | ⬜ 대기 | — | — | 전 FR 횡단 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트 첨부) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

> **갱신 규칙**: 커밋 메시지에 `(FR-NN)` 태그를 유지하면 git log 스캔으로 🔵까지 자동 반영 가능. ✅ 배포완료는 release 증거가 있을 때만 수동 체크 — 거짓 완료 차단.

---

## 배경 (Background)

B2B 운영자는 이미 다음 자산으로 운영 데이터를 보고 통제합니다.

| 기존 자산 | 위치 (`origin/dev`) | 역할 |
|---|---|---|
| MCP write tool 9종 (refund / booking / slot / inventory / notification / complimentary / facility / operatorProfile) | `presentation/mcp/toolregistry/` | AI 클라이언트가 운영을 **조작**하는 진입점 |
| 이상탐지 (`McpAnomalyDetector` + 매시간 `McpAnomalyScheduler`) | `domain/mcp/`, `presentation/mcp/` | 토큰별 호출 spike 탐지 (baseline 7일, spike 2.0배) |
| 이상 이벤트 영속화 (`mcp_anomaly_events`, V32) | `domain/mcp/McpAnomalyEvent.kt` | OPEN / RESOLVED / FALSE_POSITIVE 추적 |
| 운영자 알림센터 (`operator_inbox`, V33) | `domain/operator/inbox/` | ANOMALY / LOW_INVENTORY / BOOKING_CONFLICT / POLICY_VIOLATION / AUTOMATION_FAILURE 5종 신호 통합 |
| 인사이트 대시보드 | `web/app/portal/insights` | 시계열·KPI 시각화 |

### 현 한계 — 신호는 쌓이는데 "판단·조치"는 사람 몫

| 단계 | 현재 | 갭 |
|---|---|---|
| 탐지 | ✅ 자동 (스케줄러) | — |
| 통보 | ✅ operator_inbox 적재 | — |
| **원인 진단** | ❌ 운영자가 직접 로그·대시보드 해석 | **사람 의존, 시간·편차 큼** |
| **조치 결정** | ❌ 운영자가 어떤 MCP tool을 어떤 파라미터로 호출할지 수동 판단 | **숙련도 따라 대응 품질 격차** |
| **실행** | ⚠️ MCP write tool 수동 호출 (2-step confirm) | 도구는 있으나 "무엇을 실행할지" 판단이 없음 |
| 결과 추적·학습 | ❌ 없음 | 같은 이상 재발 시 처음부터 다시 판단 |

### 운영자 실제 불편

- 운영자 A: "BOOKING_CONFLICT 알림 받았는데, 환불해야 하는지 슬롯을 늘려야 하는지 매번 고민"
- 운영자 B: "LOW_INVENTORY 알림 → 발주량을 직접 계산 → 알림 발송까지 30분"
- 운영자 C: "새벽 이상 패턴은 다음 출근까지 방치됨"

본 PRD는 **탐지와 도구 사이의 빈 칸(진단·결정)을 AI가 메우는 의사결정 루프**를 추가합니다. MCP write tool은 그대로 두고, "무엇을 왜 실행할지"를 AI가 제안하며, 운영자는 승인만 하거나(저위험은 자동) 결과를 추적합니다.

---

## 목표 (Goals)

- operator_inbox의 5종 신호를 AI 진단 컨텍스트로 통합 수집한다 (FR-01)
- 각 신호에 대해 AI가 원인 가설 + 권장 조치안(MCP tool 호출 plan)을 생성한다 (FR-02)
- 운영자가 권장 조치를 1-click 승인하면 기존 MCP write tool로 실행된다 — 새 실행 경로를 만들지 않는다 (FR-03)
- 위험도별 자동 실행 게이트: 저위험 자동, 환불·정책성 조치는 명시적 승인 필수 (FR-04)
- 조치 결과를 audit·inbox에 기록하고, false positive를 학습해 동일 신호 재발 시 정확도를 높인다 (FR-05)
- 모든 AI 발기 조치를 `assistant=true`로 audit 마킹하고 dry-run·롤백 경로를 보장한다 (FR-06)

### 측정 가능 목표 (KPI)

| 지표 | 현재 | 목표 |
|---|---|---|
| 이상 신호 → 조치 완료 평균 시간 | 30분~1일 (사람 의존) | 5분 이내 (승인형) / 30초 (자동형) |
| 운영자 1인 일일 수동 판단 횟수 | 추정 10~30회 | 50% 감소 |
| false positive 재진단 비용 | 매번 처음부터 | 학습 후 자동 분류 |

---

## 비목표 (Non-Goals)

- **새 write 경로 신설** — AI는 기존 MCP write tool만 호출. 도구 자체 추가는 별도 PRD
- **완전 무인 운영** — 환불·정책성 조치는 항상 사람 승인 게이트 유지 (FR-04)
- **MCP v2 자동화(n8n/webhook/scheduled) 재정의** — v2는 "규칙 기반 자동화", 본 PRD는 "추론 기반 의사결정". 경계는 §아키텍처 참조
- **외부 LLM 파인튜닝** — 프롬프트 + few-shot + 룰 기반 추론으로 시작
- **B2C 사용자 대상 AI** — B2B 운영자 전용

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 운영자 | 이상 신호마다 AI가 원인과 권장 조치를 미리 정리해주기를 | 매번 직접 해석 안 해도 됨 |
| 운영자 | 권장 조치를 한 번 클릭으로 실행하기를 | 어떤 tool을 어떤 값으로 호출할지 고민 불필요 |
| 운영자 | 저위험 반복 조치(재고 알림 등)는 자동 처리되기를 | 새벽·업무외에도 즉시 대응 |
| 운영자 | 환불 같은 위험 조치는 내 승인 후에만 실행되기를 | 무단 자동 환불 사고 방지 |
| 보안 담당자 | AI가 발기한 모든 조치가 audit에 `assistant=true`로 남기를 | 사후 추적 |
| 운영자 | 잘못된 권장(false positive)을 마킹하면 다음부터 안 뜨기를 | 알림 피로 감소 |
| Legal 담당 | 자동 실행 대상 조치 종류가 명시적 정책으로 통제되기를 | 규정 준수 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. 운영 신호 통합 수집 (Signal Inbox)

**행위자**: 시스템 (스케줄러·이벤트 consumer).

**결과**:
- operator_inbox의 5종 신호(ANOMALY / LOW_INVENTORY / BOOKING_CONFLICT / POLICY_VIOLATION / AUTOMATION_FAILURE)를 AI 진단 큐에 적재
- 각 신호에 진단 컨텍스트 부착: 관련 엔티티 id(booking/slot/product/token), 최근 audit 발췌, 인사이트 집계 스냅샷
- 신규 상태값 `assistant_status` (`PENDING_DIAGNOSIS` / `DIAGNOSED` / `ACTION_PROPOSED` / `RESOLVED`)
- 신호 중복 제거: 같은 엔티티 + 같은 type은 1건으로 병합

### FR-02. AI 진단 — 원인 분석 + 권장 조치안 생성

**행위자**: AI 진단 서비스 (`AiDiagnosisGateway` — 외부 LLM, backend WAS 경유).

**트리거**: FR-01에서 `PENDING_DIAGNOSIS` 신호 적재.

**결과**:
- AI가 신호 컨텍스트를 입력받아 산출: ① 원인 가설(자연어) ② 권장 조치 plan(MCP tool 이름 + 파라미터 + 위험도) ③ 신뢰도(0~1)
- 권장 조치는 **기존 MCP write tool 시그니처로 표현** (예: `refundBooking(bookingId=123, reason=...)`)
- plan은 0~N개 — 조치 불필요 시 "관망 권장"도 유효한 출력
- 결과를 `assistant_diagnosis` 테이블에 영속화 (audit 6컬럼 + 신호 FK)

<details>
<summary>위험도 분류 기준 (초안)</summary>

| 위험도 | 조치 예시 | 기본 게이트 |
|---|---|---|
| LOW | 재고부족 알림 발송, 운영자 inbox 메모 | 자동 실행 가능 (FR-04) |
| MEDIUM | 슬롯 추가 생성, 무료 티켓 발급 | 승인 필요 |
| HIGH | 환불(refundBooking), 예약 강제 취소 | 승인 필수 + Legal 게이트 |

</details>

### FR-03. 권장 조치 승인 워크플로우 (1-click)

**행위자**: 운영자 (포털 `/portal/inbox` 확장 또는 신규 `/portal/assistant`).

**트리거**: `ACTION_PROPOSED` 신호 조회.

**결과**:
- 운영자가 권장 조치 카드에서 "승인" 클릭 → 해당 MCP write tool을 **기존 confirm flow 재사용**으로 실행 (`McpWriteToolBase.issueConfirmation` → `validateHashAndConsume`)
- 승인 시 paramsHash가 AI가 제안한 파라미터로 고정 — 운영자가 본 내용과 실행 내용 불일치 차단
- "거부" 클릭 → 신호 RESOLVED + 사유 기록 (FR-05 학습 입력)
- "수정 후 승인" → 운영자가 파라미터 조정 후 실행 (paramsHash 재계산)
- 한 신호에 복수 plan이면 운영자가 택1

### FR-04. 자동 실행 정책 (위험도별 게이트)

**행위자**: 시스템 + 정책 설정(운영자/Legal).

**결과**:
- 위험도 LOW + 운영자가 사전 활성화한 조치 종류 → AI 제안 즉시 자동 실행 (승인 생략)
- 자동 실행 대상은 운영자별 화이트리스트 (`assistant_auto_policy` 테이블): tool 이름 + 최대 위험도 + 활성 여부
- MEDIUM 이상은 항상 승인 필요
- HIGH(환불 등)는 화이트리스트에 넣어도 자동 불가 — Legal 게이트 (MCP v2 FR-01의 `automation:bypass-confirm:refund` scope 정책과 일관)
- 자동 실행 실패 시 AUTOMATION_FAILURE 신호로 inbox 재적재 + DLQ

### FR-05. 조치 결과 추적 + false positive 학습

**결과**:
- 실행된 조치의 결과(성공/실패/PG 응답)를 신호에 연결해 기록
- 운영자가 "false positive" 마킹 시 → 동일 (token/엔티티 패턴 + type) 신호는 다음부터 자동 `FALSE_POSITIVE` 분류 후 inbox 하단 접힘
- 학습 데이터는 룰 테이블(`assistant_feedback`) 기반 — 외부 학습 없이 결정적 분류
- 주간 요약: "이번 주 AI 제안 N건, 승인 X건, 자동 Y건, false positive Z건" → 인사이트 대시보드 위젯

### FR-06. 안전장치 (audit 마킹·dry-run·롤백)

**결과**:
- AI가 발기한 모든 조치는 audit log에 `assistant=true` + `diagnosis_id` 마킹
- 모든 권장 조치는 실행 전 **dry-run 미리보기** 제공 (변경될 상태를 텍스트로 표시, 실제 변경 없음)
- HIGH 위험 조치는 실행 후 롤백 경로 명시 (환불 → 재결제 링크, 취소 → 재생성 가이드)
- AI 진단 서비스 장애 시 신호는 `PENDING_DIAGNOSIS`로 유지 (유실 없음), 기존 수동 처리로 폴백

---

## 아키텍처 — MCP v2 자동화와의 경계

| 구분 | MCP v2 자동화 (별도 PRD) | C2 AI 운영 어시스턴트 (본 PRD) |
|---|---|---|
| 트리거 | 운영자가 등록한 규칙·cron·webhook | 시스템 이상 신호 (operator_inbox) |
| 의사결정 | 규칙 기반 (사람이 미리 정의) | 추론 기반 (AI가 상황 해석) |
| 실행 주체 | n8n/Zapier가 MCP tool 호출 | AI 제안 → 운영자 승인/자동 정책이 MCP tool 호출 |
| 공통 토대 | **동일한 MCP write tool + confirm flow + audit** | |

> 두 PRD는 **MCP write tool 레이어를 공유**합니다. v2는 "능동적 자동화 파이프", 본 PRD는 "수동적 이상 대응 두뇌". 상호 보완.

---

## 데이터 모델 (신규)

| 테이블 | 용도 | 주요 컬럼 |
|---|---|---|
| `assistant_diagnosis` | AI 진단 결과 | signal_id FK, cause_hypothesis, confidence, status, audit 6 |
| `assistant_action_plan` | 권장 조치 plan | diagnosis_id FK, tool_name, params_json, risk_level, executed_at, result |
| `assistant_auto_policy` | 자동 실행 화이트리스트 | operator_id, tool_name, max_risk_level, active |
| `assistant_feedback` | false positive 학습 룰 | signal_pattern, type, classification, created_by |

> operator_inbox에 `assistant_status` 컬럼 추가 (FR-01). 모든 신규 테이블은 audit 6컬럼 + soft delete + 인덱스(`be-code-convention` 준수).

---

## 영향 서비스

| 서비스 | 레포 | 변경 |
|---|---|---|
| backend (Kotlin/Spring) | `/backend` | 신규 도메인 `domain/operator/assistant/`, `AiDiagnosisGateway`(infra), inbox 확장, 진단 스케줄러 |
| B2B 웹 포털 | `/web` | `/portal/assistant` 또는 inbox 확장 + BFF route |
| 외부 LLM | — | `AiDiagnosisGateway` 구현체만 (WAS 경유, `fe-external-api-via-was` 준수) |

### Kafka 변경

| 토픽 | 유형 | 비고 |
|---|---|---|
| `assistant.action.executed.v1` | 신설 검토 | 조치 실행 결과 → 인사이트 집계 consumer (FR-05 위젯). BE 티켓 내부 흡수 가능 |

---

## 오픈 이슈 / 결정 필요

| # | 이슈 | 영향 FR | 결정권자 |
|---|---|---|---|
| 1 | 환불 자동 실행 정책 — Legal 승인 전까지 HIGH는 100% 수동 유지? | FR-04 | Legal |
| 2 | AI 진단 LLM 선택 (Claude/자체) + 비용 상한 | FR-02 | 기술 리드 |
| 3 | dry-run을 모든 위험도에 강제할지, MEDIUM↑만 할지 | FR-06 | 운영팀 |
| 4 | 신호 진단 SLA — 매시간 스케줄러 주기로 충분한지, 실시간 필요한지 | FR-01 | 운영팀 |
| 5 | false positive 학습을 룰 기반으로 시작 → 언제 ML 전환 | FR-05 | 기술 리드 |

---

## 출시 범위 (제안)

- **v1.0**: FR-01 + FR-02 + FR-03 + FR-06 — "진단 + 승인형 실행 + 안전장치". 자동 실행 없음, 전부 사람 승인.
- **v1.1**: FR-04 (LOW 위험 자동 실행) + FR-05 (학습) — 운영 데이터 축적 후.

> v1.0은 "AI가 제안하고 사람이 승인"만으로도 운영자 판단 시간을 크게 줄입니다. 자동 실행(FR-04)은 신뢰 데이터가 쌓인 뒤 개방.

---

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 작성 (C2 아이디어 → PRD) | biuea3866 |

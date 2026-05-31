# AI 운영 어시스턴트 (AI Ops Assistant) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: C2 비즈니스 확장 — MCP 서버 + 이상탐지 자산 레버리지
> 관련 PRD: [MCP v2 자동화](../20260523_mcp-server-v2-automation/prd.md), [B2B 인사이트 대시보드](../20260523_b2b-insights-dashboard/prd.md)
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 6건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/8 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-00a | operator_inbox 5종 신호 producer 신설 (선행) | ⬜ 대기 | — | — | M2 — inbox 적재 코드 0건, 전 FR 선행 |
| FR-00b | inbox entity 참조 컬럼 + mcp_audit_logs assistant 컬럼 (선행) | ⬜ 대기 | — | — | M3·M5 — 스키마 선행 |
| FR-01 | 운영 신호 통합 수집 (Signal Inbox) | ⬜ 대기 | — | — | FR-00a·00b 선행 |
| FR-02 | AI 진단 — 원인분석 + 권장 조치안 생성 | ⬜ 대기 | — | — | FR-01 선행 |
| FR-03 | 권장 조치 승인 워크플로우 (1-click) | ⬜ 대기 | — | — | FR-02 선행, confirm flow 확장 |
| FR-04 | 자동 실행 정책 (위험도별 게이트) | ⬜ 대기 | — | — | FR-03 선행, v1.1 |
| FR-05 | 조치 결과 추적 + false positive 학습 | ⬜ 대기 | — | — | FR-03 선행, v1.1 |
| FR-06 | 안전장치 (audit 마킹·dry-run·롤백) | ⬜ 대기 | — | — | 전 FR 횡단 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

> v1.0 출시 = FR-00a + FR-00b + FR-01 + FR-02 + FR-03 + FR-06 (진단 + 승인형 실행, 자동 실행 없음). v1.1 = FR-04 + FR-05.

---

## 배경 (Background)

B2B 운영자는 다음 자산으로 운영 데이터를 보고 통제합니다. **검수에서 일부 AS-IS 주장이 코드와 어긋나 정정했습니다.**

| 기존 자산 | 위치 (`origin/dev`) | 실제 상태 (검수 정정) |
|---|---|---|
| MCP **write** tool | `presentation/mcp/toolregistry/` | `McpWriteToolBase` 상속 **4클래스 / 6메서드**: `refundBooking`, `cancelBooking`, `createSlot`·`updateSlot`·`deleteSlot`, `issueComplimentaryTicket`. AI가 발기 가능한 write 액션은 이 6개로 한정 |
| MCP **read** tool | 동일 디렉토리 | `inventory`·`notification`·`facility`·`operatorProfile`·`goodsSales`·`ticketSales`·`facilityStats`는 **read tool** (`McpInventoryTools.kt:16` "MCP Read tool"). write로 오인 금지 |
| 이상탐지 (`McpAnomalyDetector` + 매시간 `McpAnomalyScheduler`) | `domain/mcp/`, `presentation/mcp/` | spike 2.0배, baseline 7일, **MIN_ABSOLUTE_THRESHOLD=50, COLD_START_DAYS=14** |
| 이상 이벤트 영속화 (`mcp_anomaly_events`, V32) | `domain/mcp/McpAnomalyEvent.kt` | OPEN / RESOLVED / FALSE_POSITIVE. **단, operator_inbox로는 전달 안 됨** |
| 운영자 알림센터 (`operator_inbox`, V33) | `domain/operator/inbox/` | enum 5종(ANOMALY 등) 정의됨. **그러나 `create()` 호출 producer가 0건 — 실제 적재되는 신호는 0종** |
| `mcp_audit_logs` (V21) | `domain/mcp/McpAuditLog.kt` | `assistant`·`diagnosis_id` 컬럼 없음. audit 6컬럼도 아님(created_at/created_by만) |

### 현 한계 — 탐지는 되나 신호 적재·진단·조치가 비어 있음

| 단계 | 현재 (검수 정정) | 갭 |
|---|---|---|
| 탐지 | ✅ 자동 (anomaly 스케줄러, mcp_anomaly_events 적재) | — |
| **통보(inbox 적재)** | ❌ **operator_inbox에 신호 쌓는 producer 0건** | **5종 신호 producer부터 신설 필요 (FR-00a)** |
| 원인 진단 | ❌ 사람이 직접 해석 | AI 진단 부재 |
| 조치 결정 | ❌ 어떤 write tool(6개)을 어떤 파라미터로 호출할지 수동 | 숙련도 격차 |
| 실행 | ⚠️ write tool 2-step confirm 수동 호출 | 판단 없음 |
| 결과 추적·학습 | ❌ 없음 | 재발 시 재판단 |

→ 본 PRD는 **(1) 신호를 inbox에 적재하는 producer를 먼저 신설**하고, **(2) 탐지~도구 사이의 진단·결정을 AI가 메우는 루프**를 얹습니다. write tool·confirm flow·audit은 재사용하되, 필요한 스키마 확장(FR-00b)을 동반합니다.

---

## 목표 (Goals)

- operator_inbox 5종 신호 적재 → 조치 완료 평균 시간 **30분~1일 → 승인형 5분 / 자동형 30초**
- 운영자 1인 일일 수동 판단 **50% 감소**
- AI 발기 조치 **`assistant=true` audit 마킹 100%** (mcp_audit_logs 컬럼 확장 전제)
- false positive 학습 후 동일 신호 **자동 분류**

---

## 비목표 (Non-Goals)

- 새 write **tool** 신설 — AI는 기존 6개 write 메서드만 호출 (도구 추가는 별도 PRD)
- 완전 무인 운영 — 환불·정책성 조치는 항상 사람 승인
- MCP v2 자동화(n8n/webhook/scheduled) 재정의 — v2는 규칙 기반, 본 PRD는 추론 기반
- 외부 LLM 파인튜닝 — 프롬프트 + few-shot + 룰 기반
- B2C 사용자 대상 AI

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 운영자 | 이상 신호마다 AI가 원인·권장 조치를 미리 정리 | 매번 직접 해석 안 함 |
| 운영자 | 권장 조치를 1-click 실행 | tool·파라미터 고민 불필요 |
| 운영자 | 저위험 반복 조치는 자동 처리 | 업무외 즉시 대응 |
| 운영자 | 환불 같은 위험 조치는 승인 후에만 실행 | 무단 자동 환불 방지 |
| 보안 담당자 | AI 발기 조치가 audit에 `assistant=true` 기록 | 사후 추적 |
| 운영자 | false positive 마킹 시 다음부터 안 뜸 | 알림 피로 감소 |
| Legal 담당 | 자동 실행 대상이 명시적 정책으로 통제 | 규정 준수 |

---

## 기능 요구사항 (Functional Requirements)

### FR-00a. operator_inbox 5종 신호 producer 신설 (선행) — 검수 M2
- **결과**: ANOMALY(anomaly 탐지 → inbox 연결), LOW_INVENTORY, BOOKING_CONFLICT, POLICY_VIOLATION, AUTOMATION_FAILURE 5종을 `OperatorInboxNotificationDomainService.create()`로 적재하는 producer를 신설. 현재 호출부가 0건이므로 본 작업이 모든 후속 FR의 전제. anomaly는 `McpAnomalyEventWorker`에서 inbox로 분기.

### FR-00b. inbox entity 참조 + audit 컬럼 확장 (선행) — 검수 M3·M5
- **결과**: `operator_inbox_notifications`에 `entity_type`·`entity_id` 컬럼 추가(현재 자유 문자열 `link`만 존재 → FR-01·05의 엔티티 매칭 불가). `mcp_audit_logs`에 `assistant BOOLEAN`·`diagnosis_id BIGINT` 컬럼 추가 + `McpAuditLogAsyncRecorder.withAudit`에 assistant 컨텍스트 주입.

### FR-01. 운영 신호 통합 수집 (Signal Inbox)
- **트리거**: FR-00a로 적재된 신호
- **결과**: 신호를 AI 진단 큐에 적재. 진단 컨텍스트(entity_type/entity_id, 최근 audit 발췌, 인사이트 스냅샷, anomaly 메타: spike_ratio·baseline·MIN_ABSOLUTE_THRESHOLD) 부착. `assistant_status`(PENDING_DIAGNOSIS / DIAGNOSED / ACTION_PROPOSED / RESOLVED). 같은 (entity_type, entity_id, type)은 1건 병합.

### FR-02. AI 진단 — 원인 분석 + 권장 조치안 생성
- **행위자**: `AiDiagnosisGateway` (외부 LLM, backend WAS 경유, PII 마스킹 `PiiMasker` 재사용)
- **결과**: ① 원인 가설 ② 권장 조치 plan(**6개 write 메서드 시그니처 중에서** tool + 파라미터 + 위험도) ③ 신뢰도(0~1). plan 0~N개("관망 권장" 유효). `assistant_diagnosis`에 영속화.
- 위험도: LOW(없음 — write 6개는 전부 상태 변경이라 inbox 메모 등 비-write 액션) / MEDIUM(createSlot·updateSlot·issueComplimentaryTicket) / HIGH(refundBooking·cancelBooking·deleteSlot).

### FR-03. 권장 조치 승인 워크플로우 (1-click) — 검수 M4
- **행위자**: 운영자 (신규 `/portal/assistant`)
- **결과**: "승인" 클릭 → write tool 실행. **confirm flow 진입 경로 명시**: 포털은 MCP principal이 아닌 운영자 세션이므로, 운영자용 승인 실행 경로를 신설(기존 2-step LLM 재호출 구조와 별개). **paramsHash 입력에 `reason` 포함**(현재 `McpRefundTools`는 reason을 hash에서 누락 → 본 PRD에서 확장). 승인자 userId를 조치 실행 주체로 바인딩(audit `created_by`). "거부" → RESOLVED + 사유(FR-05 학습). "수정 후 승인" → paramsHash 재계산.

### FR-04. 자동 실행 정책 (위험도별 게이트) — 검수 M6 (v1.1)
- **결과**: LOW(비-write 메모성) + 화이트리스트(`assistant_auto_policy`)만 자동. MEDIUM↑ 항상 승인. HIGH(환불 등) 자동 불가 — **Legal 게이트는 본 PRD가 자체 정의**(MCP v2의 `automation:bypass-confirm:refund` scope는 현재 코드에 0건 — 미구현 forward dependency이므로 의존하지 않음). 자동 실패 → AUTOMATION_FAILURE 재적재 + DLQ.

### FR-05. 조치 결과 추적 + false positive 학습 (v1.1)
- **결과**: 실행 결과를 신호에 연결 기록. "false positive" 마킹 시 동일 (entity 패턴 + type) 신호 자동 FALSE_POSITIVE 분류. 룰 테이블(`assistant_feedback`) 결정적 분류. 주간 요약 → 인사이트 위젯.

### FR-06. 안전장치 (audit 마킹·dry-run·롤백) — 검수 M5
- **결과**: AI 발기 조치 audit `assistant=true` + `diagnosis_id`(FR-00b 컬럼 전제). **dry-run 미리보기 경로 신설** — 6개 write 메서드에 변경 예정 상태를 반환하는 미리보기 추가(현재 dry-run 경로 부재). HIGH는 롤백 경로 명시(환불→재결제, 취소→재생성). AI 장애 시 PENDING_DIAGNOSIS 유지 + 수동 폴백.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 신호 진단은 매시간 스케줄러 주기 내 완료. 자동 실행은 트리거 후 30초 이내. AiDiagnosisGateway timeout 10초 + 1회 재시도.
- **보안**: AI 발기 조치 100% audit 마킹(FR-00b 전제). paramsHash에 reason 포함 + 승인자 신원 바인딩(M4). 외부 LLM은 WAS 경유 + PII 마스킹. HIGH 자동 실행 영구 차단.
- **정합성**: 진단 멱등 — 같은 신호 재진단 시 중복 plan 생성 안 함. 신호 dedup은 (entity_type, entity_id, type) 키.
- **운영**: 진단·자동실행 실패율 메트릭. DLQ 적재 시 운영팀 알림. LLM 비용 상한(일/월) + 초과 시 폴백.

---

## 제약 조건 (Constraints)

- AI가 발기 가능한 write 액션은 **기존 6개 write 메서드로 한정**. 신규 도구 금지.
- 신규 도메인 `domain/operator/assistant/`는 mcp·operator 두 도메인 데이터를 참조해야 하나 `be-code-convention`상 도메인 간 import 금지 → **application layer 오케스트레이션 + FK id only**로 조합 (검수 N4).
- 기존 `mcp_audit_logs`·`operator_inbox_notifications` 스키마 변경은 하위 호환 마이그레이션.
- Hexagonal + Rich Domain, audit 6컬럼·soft delete 준수.

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `domain/operator/assistant/`, `AiDiagnosisGateway`(infra), 신호 producer 5종, dry-run 경로, 진단 스케줄러 |
| backend | 수정 | `operator_inbox_notifications`·`mcp_audit_logs` 컬럼 추가, `McpAuditLogAsyncRecorder` 확장, 6개 write tool dry-run·paramsHash 확장 |
| web | 신규 | `/portal/assistant` + BFF route |
| 외부 LLM | 신규 연동 | `AiDiagnosisGateway` 구현체 (WAS 경유) |
| Kafka | 신설 검토 | `assistant.action.executed.v1` (인사이트 집계용 — 신설/내부흡수 오픈이슈) |

신규 테이블: `assistant_diagnosis`, `assistant_action_plan`, `assistant_auto_policy`, `assistant_feedback`.

### 확인된 누락 선행 티켓 (검수 도출)

| 제목 | 이유 |
|---|---|
| operator_inbox 5종 신호 producer 신설 (anomaly→inbox 포함) | M2 — 적재 코드 0건, FR-01 전제 |
| inbox entity_type/entity_id + mcp_audit_logs assistant/diagnosis_id 컬럼 | M3·M5 |
| 6개 write tool dry-run 미리보기 경로 | FR-06 — 현재 부재 |
| 포털 운영자용 confirm 실행 경로 + paramsHash reason 포함 | M4 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 환불 자동 실행 — HIGH 100% 수동 유지? | Legal | 착수 전 |
| 2 | AI 진단 LLM 선택 + 비용 상한 | 기술 리드 | 착수 전 |
| 3 | dry-run 모든 위험도 강제 vs MEDIUM↑ | 운영팀 | FR-06 전 |
| 4 | 신호 진단 SLA — 매시간 주기 vs 실시간 | 운영팀 | FR-01 전 |
| 5 | false positive 학습 룰→ML 전환 시점 | 기술 리드 | FR-05 전 |
| 6 | FR-03 승인자 신원 바인딩 규칙 (포털 세션 ↔ audit) | 보안 | FR-03 전 |
| 7 | assistant 마킹을 기존 audit 테이블 vs 신규 테이블 | 기술 리드 | FR-00b 전 |
| 8 | `assistant.action.executed.v1` 토픽 신설 vs BE 내부 흡수 | 기술 리드 | FR-05 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v0.1 (선행) | FR-00a + FR-00b — 신호 producer + 스키마 확장 | TBD |
| v1.0 | FR-01 + FR-02 + FR-03 + FR-06 — 진단 + 승인형 실행 + 안전장치 | TBD |
| v1.1 | FR-04 + FR-05 — 자동 실행 + 학습 | TBD |

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 write tool 9종 오류 / M2 inbox 적재 0건 / M3 entity FK 부재 / M4 paramsHash·승인경로 / M5 audit 컬럼 / M6 bypass-confirm scope 미실재 | 6건 전부 반영 — 배경 정정, FR-00a·00b 선행 신설, FR-03·04·06 보강, NFR·제약·누락티켓 추가 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M6 전부 해소 확인 (코드 기준) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | 초안 → /prd 표준 → prd-reviewer Must Fix 6건 반영 | biuea3866 |

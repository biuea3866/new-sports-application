# Notification 고도화 (채널선호·재시도·조용한시간·다국어) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: B 도메인 고도화 — 알림 신뢰성·사용자 경험
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 4건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/5 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-00 | 사용자 timezone·language + 재시도 스키마 (선행) | ⬜ 대기 | — | — | User에 필드 0건 |
| FR-01 | 사용자 채널 선호 (Preference) | ⬜ 대기 | — | — | 4채널 이미 존재 — 선호 스킵만 |
| FR-02 | 발송 실패 재시도 + DLQ (비동기 워커) | ⬜ 대기 | — | — | 현재 동기 인라인 — 신규 인프라 |
| FR-03 | 조용한 시간대 (Quiet Hours) | ⬜ 대기 | — | — | FR-00 timezone 선행 |
| FR-04 | 템플릿 다국어 | ⬜ 대기 | — | — | FR-00 language 선행 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

Notification은 **4채널 멀티채널 발송**과 템플릿 렌더를 이미 갖췄습니다. AS-IS는 검수에서 코드 대조로 크게 정정했습니다.

| 자산 (코드 확인) | 현 구조 | 한계 |
|---|---|---|
| `NotificationChannel` | **IN_APP / PUSH / EMAIL / SMS 4개 전부 정의** | 사용자별 선호 없음 — 전부 발송 |
| `SmsChannelGateway` / `EmailChannelGateway` | **SMS(SOLAPI 실연동)·EMAIL(SMTP) 구현체 이미 존재** | — (신규 추상화 불필요) |
| `dispatchNotification` (`NotificationDomainService`) | Kafka consumer → `EnqueueNotificationUseCase` → **트랜잭션 내 `gateway.send()` 동기 호출**, 실패 시 `markFailed` | 비동기 재시도 큐·DLQ 없음 |
| `NotificationStatus` | **QUEUED / SENT / FAILED 3개** (RETRYING 없음), `@Version` 보유 | 재시도 상태·attempt 추적 없음 |
| 멱등 | `enqueueOrSkip`의 `eventId` unique 1회성 | 재발송 멱등은 별개 축 |
| `TemplateRenderer` / `RenderedNotification` | `templates[templateId]` 맵, title/body 2필드 | **단일 언어**, locale 무관 |
| `User` | email/passwordHash/status만 | **timezone·language 필드 0건** |

→ 사용자가 원치 않는 채널로도 알림을 받고, 발송 실패가 동기 처리라 유실 위험, 야간 푸시·다국어 미지원입니다. **(SMS/EMAIL은 이미 있으므로 본 PRD는 "선호 제어 + 신뢰성 + 다국어"에 집중)**

---

## 목표 (Goals)

- 사용자 채널 선호 적용 → **알림 차단·이탈 감소**
- 발송 실패 재시도·DLQ로 **알림 유실 0 지향**
- 조용한 시간대로 야간 푸시 불만 감소
- 다국어 템플릿 지원

---

## 비목표 (Non-Goals)

- **신규 채널 구현 아님** — SMS/EMAIL은 이미 구현됨. 본 PRD는 선호·신뢰성·다국어
- 마케팅 캠페인 발송 — 트랜잭션 알림에 집중
- 알림 통계 대시보드 — 인사이트 PRD 별도

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 일반 사용자 | 채널별(푸시/인앱/SMS/이메일) on/off | 원하는 방식만 받음 |
| 일반 사용자 | 야간엔 푸시 안 받기 | 수면 방해 없음 |
| 외국어 사용자 | 내 언어로 알림 | 이해 가능 |
| 운영팀 | 발송 실패가 재시도·DLQ로 추적 | 알림 유실 0 |

---

## 기능 요구사항 (Functional Requirements)

### FR-00. 선행 스키마 (병목) — 검수 M4
- **결과**: 사용자 **timezone·language 저장처 결정·신설**(User 확장 vs Preference 포함 — 오픈이슈). `notifications`에 `attempt_count`·`next_retry_at`·재시도 상태 컬럼. FR-03/04/02의 선행.

### FR-01. 사용자 채널 선호 (Preference) — 검수 M1
- **결과**: `NotificationPreference` 엔티티(user_id, 채널별 on/off, 알림 유형별). **발송 경로(`dispatchNotification`)에 선호 조회 → 비활성 채널 스킵 삽입**. (SMS/EMAIL 추상화 추가 아님 — 이미 4채널 구현됨). 필수 알림(보안 등)은 선호 무시 — 분류 속성(templateId 화이트리스트 vs NotificationType enum 신설)은 오픈이슈.

### FR-02. 발송 실패 재시도 + DLQ (비동기 워커) — 검수 M2·M3
- **결과**: 현재 발송이 **동기 인라인**이므로 **재시도 비동기 워커/스케줄러는 신규 인프라**(영향 범위·제약에 명시). `NotificationStatus`에 **RETRYING 신규 추가** + 전이 규칙(FAILED→RETRYING→SENT). 재시도 멱등은 `eventId` 멱등과 **별개 축** — 같은 notification.id 동시 재시도 방지(`@Version` + attempt_count 한도). 최종 실패 시 DLQ + 운영 알림.

### FR-03. 조용한 시간대 (Quiet Hours) — 검수 M4
- **결과**: 사용자 설정 야간 시간대 푸시 보류 → 시간대 종료 후 발송 또는 인앱만. **사용자 timezone(FR-00) 선행**. 긴급 알림 예외. 현재 consumer가 PUSH·IN_APP을 항상 같이 발송하므로 "인앱 대체"의 의미(보류 후 재발송 vs 단순 스킵) 명시.

### FR-04. 템플릿 다국어 — 검수 N1·N2
- **결과**: `TemplateRenderer.render`에 locale 인자 추가(또는 templateId locale 접미). 사용자 language(FR-00) 기반 렌더. 누락 locale 기본 언어 폴백. 현재 `enrichPayload`가 발송 시점 `_title/_body` 캐싱 → 다국어 시 "저장 시점 언어 고정" 동작 명시.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 선호·조용한시간 조회는 발송 경로 — 캐시. 재시도는 비동기 워커.
- **신뢰성**: 발송 멱등(eventId 1회성 + 재시도 attempt 한도), 재시도 후 DLQ 보장. 채널 장애 시 폴백 정책.
- **정합성**: 선호 변경의 즉시 반영 vs 캐시 트레이드오프 명시(오픈이슈). 조용한 시간 타임존 정확성.
- **운영**: 채널별 성공/실패/재시도율, DLQ 적재 메트릭.

---

## 제약 조건 (Constraints)

- 기존 `NotificationChannelGateway`(4채널 구현 완료)·`TemplateRenderer`·Kafka consumer 재사용.
- 재시도 비동기 워커는 신규 컴포넌트(현재 동기 인라인). 외부 발송은 backend WAS 경유.
- Preference는 독립 Entity. audit 6컬럼·soft delete. Hexagonal + Rich Domain.

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `NotificationPreference`, 재시도 비동기 워커/스케줄러, locale 템플릿 |
| backend | 수정 | `dispatchNotification` 선호 스킵, `NotificationStatus`(RETRYING), `TemplateRenderer` locale, User/Preference timezone·language |
| mobile / web(B2C) | 신규/수정 | 알림 설정 화면(채널·조용한시간·언어) |
| Kafka | 신설 검토 | 알림 발송 DLQ 토픽 |

데이터 모델: 신규 `notification_preferences`. `notifications`에 attempt_count·next_retry_at·재시도 상태. User/Preference에 timezone·language. 템플릿 locale 키.

### 확인된 누락 선행 티켓 (검수 도출)
| 제목 | 이유 |
|---|---|
| [DB] users(또는 preference) timezone·language 컬럼 | FR-03/04 선행 (현재 0건) |
| [DB] notifications attempt_count·next_retry_at·RETRYING | FR-02 재시도 추적 |
| [INFRA] 알림 재시도 비동기 워커/스케줄러 | FR-02 신규 (현재 동기 인라인) |
| [DB] 템플릿 locale 키 규약·폴백 | FR-04 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | timezone·language 저장 위치 (User vs Preference vs 별도) | 기술 리드 | FR-00 전 |
| 2 | 필수/긴급 알림 분류 속성 (templateId 화이트리스트 vs NotificationType enum) | PO | FR-01 전 |
| 3 | 재시도 횟수·백오프·DLQ 보존 기간 | 기술 리드 | FR-02 전 |
| 4 | 선호 즉시 반영 vs 캐시 일관성 트레이드오프 | 기술 리드 | FR-01 전 |
| 5 | 조용한 시간 "인앱 대체" 의미 (보류 후 재발송 vs 스킵) | PO | FR-03 전 |
| 6 | 지원 언어 목록 + 번역 관리 주체 + 캐시된 _title/_body 다국어 처리 | PO | FR-04 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v0.0 (선행) | FR-00 — timezone·language·재시도 스키마 | TBD |
| v1.0 | FR-01 + FR-02 — 채널 선호 + 재시도/DLQ | TBD |
| v1.1 | FR-03 — 조용한 시간대 | TBD |
| v1.2 | FR-04 — 다국어 | TBD |

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 SMS/EMAIL 이미 구현(추상화 추가 중복) / M2 NotificationStatus RETRYING 없음·멱등 모델 / M3 발송 동기→재시도 큐는 신규 인프라 / M4 User timezone·language 0건 | 4건 전부 반영 — AS-IS 4채널 정정, FR-01 선호 스킵으로 범위 축소, FR-02 신규 인프라·RETRYING 명시, FR-00 timezone/language 선행 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M4 전부 해소 확인 (코드 기준) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | /prd 초안 → prd-reviewer Must Fix 4건 반영 | biuea3866 |

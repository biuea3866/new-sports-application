# 커뮤니티 고도화 (실시간채팅·모더레이션·멘션) PRD

> 작성일: 2026-05-31 | 작성자: biuea3866@gmail.com
> 소스: B 도메인 고도화 — Post/Message 커뮤니티 활성화
> prd-reviewer 검수: 2026-05-31 NEEDS_REVISION → Must Fix 3건 반영 → **재검수 APPROVED** (하단 검수 이력)

---

## 진행 현황 (Status Ledger)

> 최종 갱신: 2026-05-31 | 전체: 0/3 배포완료 (0%)

| FR | 기능 | 상태 | 구현(PR) | 배포 | 비고 |
|----|------|------|---------|------|------|
| FR-01 | 실시간 채팅 (WebSocket/SSE) | ⬜ 대기 | — | — | AUTH-03 선행 검토 |
| FR-02 | 신고·모더레이션 (Post/Comment/Message) | ⬜ 대기 | — | — | 신고 적재 + 임계 숨김 분리 |
| FR-03 | 멘션·읽음 표시 | ⬜ 대기 | — | — | Notification consumer 경유 |

### 상태 정의 (증거 기반 — `rules/COMPLETION-RULE.md` 연계)

| 상태 | 진입 조건 (증거) |
|---|---|
| ⬜ 대기 | 티켓만 존재 |
| 🟡 진행중 | 브랜치 존재 / 커밋 시작 |
| 🔵 구현완료 | dev 머지 PR + COMPLETION-RULE §1~4 충족 (테스트 통과 아티팩트) |
| ✅ 배포완료 | release 태그 / 배포 로그 첨부 |

---

## 배경 (Background)

커뮤니티는 게시판(Post/Comment)과 채팅방(Message/Room)을 갖췄으나 정적·미보호입니다. AS-IS는 검수에서 코드 대조로 정정했습니다 — **전부 JPA/MySQL입니다(Mongo 아님)**.

| 자산 (코드 확인) | 현 구조 | 한계 |
|---|---|---|
| `Post` / `Comment` | **`@Entity`+`@Table`+`JpaAuditingBase`** (Flyway `V9__create_posts_comments.sql`) | 신고·모더레이션 없음 |
| `Message` / `Room` / `RoomParticipant` | **`@Entity`+`JpaAuditingBase`** (Flyway `V8__create_messages_rooms.sql`) | 실시간 전송 없음 |
| 메시지 전송 | `MessageApiController#sendMessage`(`POST /rooms/{roomId}/messages`) → `SendMessageUseCase` → `MessageDomainService.sendMessage` | **BE에 push 채널 없음, REST POST 단건 전송** |
| 방 참여자 검증 | `roomParticipantRepository.existsByRoomIdAndUserId` | 재사용 가능 |
| 인증 | `@RequestHeader("X-User-Id")` + `// TODO(AUTH-03): SecurityContext로 교체` | **JWT/SecurityContext 미정착** |
| Notification 연계 | `NotificationEventWorker`(consumer) → `EnqueueNotificationUseCase` → `enqueueOrSkip`(eventId 멱등) | 멘션 알림 경로로 재사용 가능 |

→ 채팅이 실시간이 아니고, 신고·차단 수단이 없으며, 멘션·읽음이 빠져 커뮤니티 활성도가 낮습니다.

---

## 목표 (Goals)

- 메시지 실시간 전송으로 채팅 사용성 개선 (REST 단건 → push)
- 신고·모더레이션으로 부적절 콘텐츠 처리 경로 확보
- 멘션·읽음 표시로 상호작용 증대

---

## 비목표 (Non-Goals)

- AI 자동 모더레이션 — 룰 기반 + 관리자 수동
- 음성·영상 채팅 — 텍스트에 집중
- 대규모 그룹(수천명) 실시간 — 기본 규모부터

---

## 사용자 스토리 (User Stories)

| As a | I want to | So that |
|---|---|---|
| 사용자 | 메시지를 실시간으로 주고받기 | 새로고침 없이 대화 |
| 사용자 | 부적절한 글·메시지 신고 | 안전한 커뮤니티 |
| 관리자 | 신고 누적 콘텐츠 검토·처리 | 모더레이션 |
| 사용자 | @멘션 + 읽음 확인 | 원활한 소통 |

---

## 기능 요구사항 (Functional Requirements)

### FR-01. 실시간 채팅 (WebSocket/SSE) — 검수 M3
- **행위자**: 채팅방 참여자
- **결과**: 메시지 작성 시 같은 방 참여자에게 실시간 전송(WebSocket/SSE — 단방향이면 SSE, 오픈이슈). **방 참여자 검증은 `existsByRoomIdAndUserId` 재사용**. 연결 인증은 현재 `X-User-Id` 헤더 + AUTH-03 TODO 상태이므로 **WS 토큰 전달 방식 + AUTH-03 선행 여부를 오픈이슈로** 명시(브라우저 WS는 커스텀 헤더 제약). 실시간 전송 시점은 `sendMessage` 트랜잭션 **AFTER_COMMIT**(기존 `@TransactionalEventListener` 패턴 재사용) — 전송 성공 ≠ 저장 누락. DB 영속화 유지(전송은 추가 레이어).

### FR-02. 신고·모더레이션 (Post/Comment/Message)
- **결과**: `Report` 엔티티(대상 유형 POST/COMMENT/MESSAGE, 대상 id, 신고자, 사유, status). **"신고 적재"와 "임계 자동 숨김 정책"을 별도 티켓 분리**(3개 대상 soft-hide 얽힘). 누적 신고 임계 초과 시 자동 숨김(soft delete) + 관리자 검토(삭제·경고·기각). 모더레이션 권한은 기존 ADMIN role 재사용 여부 오픈이슈.

### FR-03. 멘션·읽음 표시 — 검수 N1
- **결과**: 메시지·댓글 @멘션 → **멘션 이벤트 발행 → `NotificationEventWorker` consumer → `EnqueueNotificationUseCase` 경유**(직접 발송 금지 원칙 일치, `eventId` 멱등으로 중복 알림 방지). 읽음 표시(`RoomParticipant`에 last_read) → 안 읽은 수. 멘션 파싱 규칙(@닉네임 vs @userId)·공통 파싱 위치(도메인 간 참조 금지 → application 또는 common)는 오픈이슈.

---

## 비기능 요구사항 (Non-Functional Requirements)

- **성능**: 실시간 전송 지연 1초 이내(로컬). 룸별 구독으로 연결 확장. 읽음 표시 배치 갱신 가능.
- **보안**: WS/SSE 연결 인증(토큰 방식 오픈이슈) + 방 참여자 검증(`existsByRoomIdAndUserId`, 타 방 구독 차단). 신고는 본인 1회.
- **정합성**: 실시간 전송과 DB 영속화 일관(AFTER_COMMIT 발행). 읽음 카운트 정확성.
- **운영**: 연결 수·메시지 처리율·신고 처리 시간 메트릭.

---

## 제약 조건 (Constraints)

- Post/Message는 **JPA/MySQL + Flyway + `JpaAuditingBase`**(audit 6컬럼·soft delete). 신규 `reports` 테이블·`messages`/`room_participants` 컬럼은 **Flyway 마이그레이션**(MongoDB/BaseDocument 아님).
- `Report`는 독립 Entity(`@ManyToMany` 금지).
- 멘션 알림은 Notification 도메인 이벤트/UseCase 경유(직접 발송 금지).
- 실시간 전송은 presentation layer(WebSocket/SSE handler = 외부 진입점).

---

## 영향 범위 (Scope)

| 레포 | 변경 유형 | 설명 |
|---|---|---|
| backend | 신규 | `Report` 엔티티·신고 UseCase, WebSocket/SSE handler(presentation), 멘션 이벤트 |
| backend | 수정 | `messages`/`room_participants`에 멘션·last_read, 관리자 모더레이션 |
| mobile / web(B2C) | 신규/수정 | 실시간 채팅 UI, 신고 버튼, 멘션·읽음, 관리자 모더레이션 화면 |
| 이벤트 | 활용 | 멘션 → `NotificationEventWorker` consumer |

데이터 모델: 신규 `reports`(Flyway). `messages`/`room_participants`에 멘션·last_read 컬럼(Flyway).

### 확인된 누락 선행 티켓 (검수 도출)
| 제목 | 이유 |
|---|---|
| [DB] reports + messages/room_participants 컬럼 (Flyway, JPA) | 신규 스키마 — Mongo 아님 |
| [BE] 신고 적재 / 임계 자동 숨김 정책 분리 | FR-02 1티켓 초과 방지 |

---

## 오픈 이슈 (Open Issues)

| # | 질문 | 담당 | 기한 |
|---|------|------|------|
| 1 | 실시간 방식 — WebSocket vs SSE (단방향이면 SSE) | 기술 리드 | FR-01 전 |
| 2 | WS 인증 토큰 전달 방식 + AUTH-03(SecurityContext) 선행 여부 | 보안/기술 리드 | FR-01 전 |
| 3 | 연결 인프라 — STOMP/Redis pub-sub 스케일아웃 필요 여부 | 기술 리드 | FR-01 전 |
| 4 | 자동 숨김 신고 임계 (몇 건?) + 모더레이션 권한(ADMIN role 재사용?) | PO/보안 | FR-02 전 |
| 5 | 멘션 파싱 규칙(@닉네임 vs @userId) + 공통 파싱 위치 + 알림 빈도 제한 | PO/기술 리드 | FR-03 전 |
| 6 | 멘션 대상 범위 — Post 본문 포함 여부 (Comment·Message만?) | PO | FR-03 전 |

---

## 마일스톤

| 단계 | 내용 | 목표일 |
|------|------|--------|
| v1.0 | FR-02 — 신고·모더레이션 (안전, 인프라 부담 적음) | TBD |
| v1.1 | FR-01 — 실시간 채팅 (인프라·AUTH-03 결정 후) | TBD |
| v1.2 | FR-03 — 멘션·읽음 | TBD |

---

## 검수 이력 (prd-reviewer)

| 일자 | 판정 | Must Fix | 반영 |
|---|---|---|---|
| 2026-05-31 | NEEDS_REVISION | M1 Post/Message는 Mongo 아닌 JPA/MySQL(BaseDocument 전제 오류) / M2 "폴링 추정"을 근거화 / M3 WS 인증 ↔ 현행 X-User-Id·AUTH-03 미정착 간극 | 3건 전부 반영 — JPA/Flyway 기준 전면 정정, 확인된 사실로 교체, WS 인증·AUTH-03 오픈이슈, existsByRoomIdAndUserId·AFTER_COMMIT·NotificationEventWorker 경로 명시 |
| 2026-05-31 | **APPROVED** (재검수) | M1~M3 전부 해소 확인 (코드 기준) | 구현 착수 가능 |

## Document History

| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-31 | /prd 초안 → prd-reviewer Must Fix 3건 반영 | biuea3866 |

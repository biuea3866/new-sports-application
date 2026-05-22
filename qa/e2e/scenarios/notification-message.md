# E2E-08 알림 · 메시지

## 메타
- severity: Major
- layer-hint: FULL-STACK
- related-files:
  - backend/src/main/kotlin/com/sportsapp/presentation/notification/NotificationApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/message/MessageApiController.kt
  - backend/src/main/kotlin/com/sportsapp/presentation/message/RoomApiController.kt
- related-ticket: none
- estimated-duration: 1m

## 사전 조건
- DB 시드: `qa/e2e/fixtures/notif-and-room.sql`
  - user-A: 미읽음 알림 3건 + 읽음 알림 2건
  - room-1: user-A와 user-B 참여, 메시지 5건 존재
- 인증 상태: user-A, user-B (room 참여자), user-C (room 미참여자)
- 환경 변수: 없음

## 시나리오 (Given/When/Then 한 줄)
| ID | Given | When | Then |
|---|---|---|---|
| E2E-08-01 | user-A | `GET /notifications/me`를 호출할 때 | 총 5건(미읽음 3 + 읽음 2)이 페이지로 반환된다 |
| E2E-08-02 | user-A + `onlyUnread=true` | `GET /notifications/me?onlyUnread=true`를 호출할 때 | 미읽음 3건만 반환된다 |
| E2E-08-03 | user-A | `GET /notifications/me/unread-count`를 호출할 때 | 200과 unreadCount=3이 반환된다 |
| E2E-08-04 | user-A + 알림 id `notif-101` | `PATCH /notifications/{id}/read`를 호출할 때 | 200과 read=true 상태의 알림이 반환되고 unread-count는 2로 감소한다 |
| E2E-08-05 | user-A + room-1 | `POST /rooms/1/messages`에 내용을 보낼 때 | 201 Created와 새 메시지가 반환된다 |
| E2E-08-06 | user-B + room-1 | `GET /rooms/1/messages`를 호출할 때 | E2E-08-05의 메시지를 포함한 총 6건이 시간 역순으로 반환된다 |

## 회귀 케이스 (변경 후에도 깨지면 안 되는 기존 동작)
| ID | 케이스 |
|---|---|
| E2E-08-R01 | 메시지 목록의 cursor 기반 페이징이 동작하며 다음 페이지 cursor가 응답에 포함된다 |
| E2E-08-R02 | 알림 페이징 기본값(page=0, size=20)이 명시되지 않은 요청에서도 유지된다 |

## 엣지 케이스
| ID | 케이스 |
|---|---|
| E2E-08-E01 | 다른 사용자의 알림 id로 `PATCH /notifications/{id}/read` 호출 시 403/404가 반환된다 |
| E2E-08-E02 | room 미참여자(user-C)가 `POST /rooms/1/messages` 호출 시 403이 반환된다 |
| E2E-08-E03 | 알림 0건인 user-D가 `unread-count` 호출 시 200과 unreadCount=0이 반환된다 |
| E2E-08-E04 | 빈 메시지 내용으로 `POST /rooms/1/messages` 호출 시 400 Bad Request가 반환된다 |

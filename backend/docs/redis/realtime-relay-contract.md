# social 실시간 STOMP 릴레이 Redis 채널 계약 (W1-07)

근거 설계: `/Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/아키텍트/20260728-msa-물리분리-실행설계.md` §9-3(OQ-7 확정), §7-3(예산 영향), §11-1 W1-07, §8-3 완료 판정 ⑦
근거 티켓: `tickets/W1-07-Redis-pubsub-STOMP-릴레이.md`

`social`이 STOMP 실시간 세션을 2 replica로 운용하려면, 인스턴스 A에 접속한 사용자의 메시지가
인스턴스 B에 접속한 사용자에게도 도달해야 한다. `enableStompBrokerRelay`(RabbitMQ/ActiveMQ 필요)는
**신규 컨테이너 300~500 MiB**가 들어 미채택 — 기존 공유 Redis pub/sub로 인스턴스 간 팬아웃한다
(신규 컨테이너 0).

## 1. 방식 — 로컬 전달 + Redis 발행/구독

| 흐름 | 처리 |
|---|---|
| 발신 | `MessageBroadcastGatewayImpl`이 자기 로컬 WebSocket 세션에 즉시 전달(`LocalStompBroadcaster`) + Redis 채널에 발행(`RealtimeRelayPublisher`) |
| 수신 | `RealtimeRelaySubscriber`가 채널을 구독 → **자기 로컬 세션에만** 전달(`LocalStompBroadcaster`) |

## 2. 채널 설계

| 채널 패턴 | 자료구조 | TTL | 무효화 트리거 | 근거 |
|---|---|---|---|---|
| `social:realtime:relay` (기본값, `RealtimeRelayProperties.channel`) | pub/sub 채널 (메시지 미저장) | 해당 없음 — pub/sub 채널은 키가 아니라 구독 경로라 TTL 개념이 없다(메시지를 저장하지 않으므로 `maxmemory` 미소비) | 해당 없음(고정 채널, 생성/삭제 없음) | 아래 "채널 단위 선택 근거" 참고 |

### 채널 단위 선택 근거 — 단일 브로드캐스트 채널 + 인스턴스 로컬 필터 (방 단위 채널 미채택)

- **미채택: 방(room) 단위 채널**(`social:realtime:relay:room:{roomId}`) — 방 개수는 가변·무제한이라
  서비스 성장에 따라 구독 채널 수가 함께 늘어난다. `RedisMessageListenerContainer`가 방마다 별도
  `ChannelTopic`을 관리해야 해 구독 등록/해제 관리 비용이 방 생성·삭제와 결합된다.
- **채택: 단일 브로드캐스트 채널** — 인스턴스 수만큼만 구독(2 replica 목표 기준 구독 2개 고정)하고,
  방 라우팅은 payload 의 `roomId` 필드 + 로컬 STOMP 목적지(`/topic/rooms/{roomId}`)에 위임한다.
  트레이드오프: 모든 인스턴스가 무관한 방의 메시지까지 수신해 판별(`RealtimeRelaySubscriber`가
  역직렬화)하지만, FR-6 목표(300세션 저빈도 텍스트) 규모에서는 이 오버헤드가 무시할 수준이다.
  방 개수가 방 단위 채널을 정당화할 규모로 커지면(예: 수천 개 동시 활성 방) 재검토한다.

## 3. 메시지 스키마 — `RealtimeRelayEnvelope` (JSON, sealed 판별 프로퍼티)

공통 필드:

| 필드 | 타입 | 설명 |
|---|---|---|
| `eventType` | String | 판별 프로퍼티 — `MESSAGE` / `TYPING` / `READ` (다형 역직렬화 기준, Kafka `PaymentEvent`와 동일 관례) |
| `senderInstanceId` | String | 발행 인스턴스 식별자(`RelayInstanceId`, 프로세스 생명주기 동안 고정) — 자기 발행 메시지 폐기(중복 방지) 판별 키 |
| `roomId` | Long | 로컬 전달 시 STOMP 목적지(`/topic/rooms/{roomId}`) 결정 |

타입별 추가 필드:

| eventType | 추가 필드 |
|---|---|
| `MESSAGE` | `messageId`(Long), `userId`(Long), `content`(String), `createdAt`(ZonedDateTime) |
| `TYPING` | `userId`(Long), `typing`(Boolean) |
| `READ` | `userId`(Long), `lastReadMessageId`(Long) |

### 예시 payload

```json
{"eventType":"MESSAGE","senderInstanceId":"a1b2c3d4-...","roomId":5,"messageId":101,"userId":10,"content":"안녕","createdAt":"2026-07-31T10:00:00+09:00"}
```

### 하위 호환

- 필드 추가는 optional만. 필수 필드 변경·제거는 새 채널(`social:realtime:relay:v2` 형태)로 이관 후 구 채널 폐기.

## 4. 자기 발행 메시지 폐기 (중복 방지)

발신 인스턴스는 로컬 전달과 Redis 발행을 모두 수행하므로, 같은 채널을 재구독하면 자기 메시지를
다시 수신해 두 번 전달된다. `RealtimeRelaySubscriber`가 `envelope.senderInstanceId ==
relayInstanceId.value`면 폐기하고 아무 것도 하지 않는다.

## 5. Redis 설정 검토 결과

| 항목 | 검토 결과 |
|---|---|
| `client-output-buffer-limit pubsub` | **변경 불필요** — `docker-compose.yml`이 이 값을 override하지 않아 Redis 내장 기본값(hard 32mb, soft 8mb/60s)을 사용한다. FR-6 목표(300세션, 저빈도 텍스트, payload 수백 바이트)에서 이 상한은 충분히 여유 있다. 느린 구독자가 발생하면 Redis가 **그 pubsub 클라이언트 연결만** 끊어 서버 전체에는 영향이 없다(설계상 방어 동작). |
| `maxmemory-policy noeviction` | **변경 불필요** — 이미 설정됨(`docker-compose.yml:117`). pub/sub은 메시지를 키로 저장하지 않으므로 `maxmemory` 자체를 소비하지 않아 이 릴레이가 evict 정책에 영향을 주지 않는다. |
| `maxmemory` | **변경 불필요** — dev 256 MiB(`docker-compose.yml:116`) / prod 512 MiB 유지. |

## 6. 순서·유실 성격 (명시)

- Redis pub/sub은 **전달 보장이 없다** — 구독자가 순간적으로 없거나(재기동 중 등) 연결이 끊긴
  상태면 그 순간 발행된 메시지는 유실된다. 순서 보장도 없다(멀티 인스턴스 팬아웃 특성상).
- **이 유실은 데이터 유실이 아니다.** 채팅 메시지는 `messages` 테이블에 먼저 영속화되고
  (`MessageDomainService.sendMessage`), 실시간 팬아웃은 커밋 이후 별도 트랜잭션(`AFTER_COMMIT`
  이벤트 리스너 → `MessageBroadcastEventWorker` → `MessageDomainService.broadcastMessage`)에서
  일어난다. 두 경로는 완전히 분리되어 있어, 실시간 전달이 실패해도 재접속 시 `listMessages()`
  REST 조회로 항상 복구된다(`MessageDomainServiceSendMessagePersistenceIndependentOfBroadcastTest`).
- **승격 기준** — 향후 "실시간 전달 자체가 유실 불가"인 요건(예: 결제 상태 실시간 알림처럼
  놓치면 안 되는 신호)이 생기면, 이 pub/sub 채널을 Redis Stream(consumer group)으로 승격해
  전달 보장을 획득해야 한다. 현재 채팅 텍스트 팬아웃은 DB 백업이 있어 이 요건에 해당하지 않는다.

## 7. 세션 어피니티

불필요 — WebSocket은 지속 TCP 연결이라 업그레이드가 성립한 인스턴스에 자연히 고정된다.
nginx `ip_hash`는 도입하지 않는다(§9-3).

## 8. 롤백

`chat.realtime.relay.enabled=false`(env `CHAT_REALTIME_RELAY_ENABLED`)로 재기동하면
`RealtimeRelayPublisher.publish()`가 즉시 no-op이 되어 로컬 세션 전달만 수행하는 상태로 완전
복귀한다 — 단일 인스턴스 운영에서는 동작 차이가 0이다. `chat.realtime.enabled`(`application.yml`)의
기존 의미(WebSocket 전송 계층 전체 on/off)는 이 작업에서 변경하지 않는다.

## 9. 예시 값

```
social:realtime:relay → PUBLISH 채널 (키가 아니라 pub/sub 채널 — RDB/AOF 대상 아님)
```

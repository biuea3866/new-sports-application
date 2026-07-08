# FeatureFlag Redis 키·pub/sub 채널 계약

`REDIS-01` 산출물. BE-03(be-implementer)이 `RedisTemplate`·`CacheConfig`·pub/sub 리스너 등 앱 코드를
작성할 때 이 문서 하나만으로 구현 가능하도록 자족적으로 작성한다. 근거 TDD:
`/Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/피처 플래그/TDD.md` "방안 비교 — 변경 전파" / "Observability".
컨벤션 SSOT: `~/.claude/rules/private-redis-convention.md`.

## 1. 캐시 키 계약 (look-aside)

| 항목 | 값 |
|---|---|
| 키 패턴 | `featureflag:flag:{flagKey}` |
| 자료구조 | String — JSON 직렬화된 `FeatureFlagSnapshot` 1건. Hash를 쓰지 않는 이유: 필드 단위 부분 갱신이 없고 항상 스냅샷 전체를 원자적으로 read/write하므로 String이 더 단순하고 원자성 보장이 쉽다 |
| TTL | 600초(10분) 고정. `SETEX`/`SET ... EX 600` |
| TTL 선택 근거 | pub/sub broadcast가 1차 무효화 수단(즉시 전파)이고, TTL은 pub/sub 유실 시 안전망(상한). 600초는 `@Scheduled`(30초) 전체 리프레시 주기(TDD Terminology "로컬 스냅샷") 대비 20배 여유를 둬 캐시 미스 폭주를 방지하면서도 무기한 stale을 막는다 |
| 무효화 트리거 | 플래그 생성/수정/아카이브 커밋 시(AFTER_COMMIT) 쓰기 인스턴스가 ① 해당 키를 최신 스냅샷으로 `SET`(갱신, 삭제 아님 — 즉시 재조회 가능하게) ② §2 채널로 broadcast. 캐시 미스(TTL 만료 포함) 시에는 MySQL(SSOT)에서 조회해 `SET ... EX 600`으로 채운다 |
| stampede 방지 | 개별 플래그 캐시는 핫키 단일 조회가 아니라 부트스트랩 시 MySQL 전량 조회(`findAllActive`) 후 개별 키를 채우는 구조라 캐시 미스가 동시다발적으로 원본에 몰리지 않는다. 별도 TTL jitter·논리 만료는 불필요(트래픽 패턴상 스탬피드 위험 낮음) |
| 스캔 금지 | `KEYS featureflag:flag:*` 사용 금지. 부트스트랩(인스턴스 기동 시 전체 로드)은 캐시가 아니라 MySQL `findAllActive`로 수행하고, 조회된 각 플래그를 개별 키로 채운다. 캐시 인덱스 Set은 두지 않는다(현재 조회 패턴에 근거 없음) |
| 예시 키 | `featureflag:flag:demo.feature.hello` |
| 예시 값 (JSON) | `{"key":"demo.feature.hello","type":"RELEASE","status":"ACTIVE","strategy":{"strategyType":"GLOBAL_TOGGLE","enabled":true},"description":"demo"}` |

### FeatureFlagSnapshot 필드 (BE-03 DTO 매핑 참고)

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `key` | String | Y | 플래그 식별자 (`flagKey`) |
| `type` | String | Y | 플래그 타입 (`RELEASE`/`OPERATIONAL`/`EXPERIMENT`/`ENTITLEMENT` — TDD.md:238,408 · design-db.md:46 · V41__create_feature_flags.sql:14 도메인 enum과 동일) |
| `status` | String | Y | `ACTIVE`/`ARCHIVED` 등 |
| `strategy` | Object | Y | 평가 전략 (다형성 — `strategyType` 판별 필드 포함) |
| `description` | String | N | 설명 |

## 2. pub/sub 채널 계약

| 항목 | 값 |
|---|---|
| 채널명 | `featureflag:changes` |
| 전달 보장 | 유실 허용 — Redis pub/sub는 구독자가 없거나 순간 끊기면 메시지가 유실된다. 이 채널은 "즉시성" 최적화용이고, 유실분은 각 인스턴스의 `@Scheduled`(30초) 전체 리프레시가 최종적으로 수렴시킨다(TDD "로컬 스냅샷" 폴백 전략과 동일 축). 유실 불가 요건이 아니므로 Stream+consumer group은 채택하지 않는다 |
| 메시지 포맷 | JSON. `{"flagKey": "demo.feature.hello", "occurredAt": "2026-07-04T09:00:00.000Z"}` |
| 메시지 필드 | `flagKey`(String, 필수, 변경된 플래그 식별자) / `occurredAt`(String, 필수, ISO-8601 UTC, 전파 지연 측정용 — 수신 시각 - occurredAt = 전파 지연) |
| key 설계 | 채널은 단일 고정 채널이며 순서 보장 대상이 아니다. 동일 flagKey에 대한 연속 변경 순서가 뒤바뀌어도 구독자는 항상 재조회(§ 구독자 동작)로 최신값을 가져오므로 무해하다 |
| 발행 시점 | MySQL 쓰기 트랜잭션 커밋 후(`AFTER_COMMIT`) — 커밋 전 발행 시 구독자가 재조회했을 때 아직 반영 안 된 구값을 읽는 경쟁을 방지 |
| 구독자 동작 | 메시지 수신 시 `flagKey`로 §1 캐시 키를 재조회(캐시 미스면 MySQL 폴백) → 로컬 인메모리 스냅샷을 덮어쓴다. 동일 메시지를 중복 수신해도 "재조회 후 덮어쓰기"는 멱등 — 별도 멱등 키(eventId) 불필요. `occurredAt`과 수신 시각의 차이를 전파 지연 지표로 기록(Observability BE-12 연계) |
| 발행자 커넥션 | `RedisTemplate.convertAndSend` — 별도 커넥션 불필요(요청 처리 스레드가 사용하는 커넥션 풀 공유) |
| 구독자 커넥션 | §3 참조 |

## 3. 서버 설정 노트 (docker compose)

- 이번 과제로 인한 `docker-compose.yml`/`docker-compose.prod.yml`의 `redis` 서비스 정의 변경 **없음**.
  캐시(featureflag 키)와 세션·락·기존 캐시(랭킹 등)가 같은 Redis 인스턴스·키스페이스를 공유하지만,
  기존 eviction 정책(compose에 명시적 `maxmemory`/`maxmemory-policy` 미설정 = Redis 기본값 `noeviction`)을
  그대로 유지한다 — 이번 과제 범위에서 변경 근거 없음. eviction 정책 변경이 필요하다고 판단되면
  파괴적 변경([private-redis-convention](~/.claude/rules/private-redis-convention.md))이므로 별도 티켓·사용자 확인을 거친다.
- pub/sub는 메모리에 상주하는 데이터가 아니다(발행 즉시 소멸, 큐잉되지 않음) — `maxmemory` 산정에 포함하지 않는다.
- **pub/sub 리스너는 애플리케이션 인스턴스마다 전용 구독(subscribe) 커넥션을 1개씩 점유한다**
  (`RedisMessageListenerContainer`가 내부적으로 커넥션을 하나 열어 유지). 인스턴스를 스케일아웃할수록
  구독 커넥션 수가 선형 증가하므로, `redis.conf`의 `maxclients`(기본 10000) 여유를 인스턴스 수 대비 확인한다.
  현재 로컬/스테이징 규모(단일~수 인스턴스)에서는 여유가 충분해 별도 조정 불필요. 인스턴스 수가
  늘어나는 시점에 Observability(BE-12 구독 커넥션 게이지)로 재평가한다.

## 4. BE-03 구현 포인트 요약 (넘겨줄 지점)

- **무효화 지점**: 플래그 생성/수정/아카이브 UseCase의 `@Transactional` 커밋 후(`@TransactionalEventListener(AFTER_COMMIT)`)
  ① `featureflag:flag:{flagKey}` 키를 최신 스냅샷으로 `SET EX 600` ② `featureflag:changes` 채널에
  `{flagKey, occurredAt}` 발행. 두 동작은 같은 이벤트 핸들러에서 순서대로 수행(캐시 갱신 → 발행).
- **캐시 조회 실패(미스/장애) 폴백**: MySQL `findAllActive`/`findByKey` 직접 조회 → 성공 시 캐시 재적재.
  Redis 자체 장애 시에는 TDD "로컬 스냅샷" 정책대로 마지막 성공 스냅샷을 계속 사용(캐시 계층 우회).
- **구독자(EventWorker 성격)**: `RedisMessageListenerContainer` + `MessageListener` 구현체 1개,
  `featureflag:changes` 단일 채널만 구독. 수신 시 캐시 재조회 → 로컬 스냅샷 갱신 → 전파 지연 로그/지표 기록.
- **KEYS 금지 확인 포인트**: 코드 리뷰 시 `redisTemplate.keys(...)` 호출이 없는지 확인(부트스트랩은 MySQL 경유).
- **사용자 확인 대기 항목**: 없음 (compose 변경 없음, 채널·키 네임스페이스는 초기 확정으로 파괴적 변경 회피).

## 5. 로컬 검증 (redis-cli)

검증 절차와 raw 출력은 본 문서 대신 REDIS-01 완료 보고에 첨부한다(문서 자체는 계약 산출물).
재현 절차만 기록:

```bash
docker run -d --name redis-featureflag-verify -p 16379:6379 redis:7-alpine

# 캐시 키 계약 검증
redis-cli -p 16379 SET featureflag:flag:demo.feature.hello \
  '{"key":"demo.feature.hello","type":"RELEASE","status":"ACTIVE","strategy":{"strategyType":"GLOBAL_TOGGLE","enabled":true},"description":"demo"}' \
  EX 600
redis-cli -p 16379 TTL featureflag:flag:demo.feature.hello
redis-cli -p 16379 TYPE featureflag:flag:demo.feature.hello
redis-cli -p 16379 GET featureflag:flag:demo.feature.hello

# pub/sub 채널 계약 검증 (별도 터미널)
redis-cli -p 16379 SUBSCRIBE featureflag:changes
redis-cli -p 16379 PUBLISH featureflag:changes '{"flagKey":"demo.feature.hello","occurredAt":"2026-07-04T09:00:00.000Z"}'
```

## Document History

| 날짜 | 변경 내용 |
|---|---|
| 2026-07-04 | 최초 작성 (REDIS-01) |

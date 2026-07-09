# 가상 대기열(Virtual Queue) Redis 키 계약

근거 TDD: `/Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/가상 대기열 트래픽 제어/20260709-가상대기열-tdd.md` (Redis 키 계약·인터페이스 시그니처·실패 경로)
근거 리뷰: `/Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/가상 대기열 트래픽 제어/20260709-redis-contract.md` (§0 블로킹 교정 4건 반영 완료 — 이 문서가 최종 계약)
근거 티켓: `tickets/BE-01-virtualqueue-contracts-lua-keys.md`

이 문서는 `VirtualQueueStoreImpl`(후행 티켓)이 구현 기준으로 삼는 계약이다. 기존
`limited-drop-keys.md`(게이트 카운터) 형식을 따른다.

## 핵심 교정 사항 (§0, 리뷰에서 확정)

- **admission 판정은 `ZRANK`가 아니라 고정 시퀀스(`ZSCORE`)로 한다.** `ZRANK`는 살아있는 멤버 기준
  상대 순위라, 앞선 멤버가 admission으로 제거되면 뒤 멤버의 rank가 그 즉시 앞당겨져 같은 틱 안에서
  연쇄 과다 admission이 발생한다. 진입 시 `ZADD`의 score를 `now(ms)`가 아니라 `queue:{type}:{id}:seq`
  (`INCR` 채번)로 부여하고, admission 판정은 이 고정 시퀀스와 `admitted_count`를 비교한다.
  `ZRANK`는 표시용(`aheadCount`)으로만 계속 쓴다.
- **`admit.lua`의 `seenTotal` 상한 원천은 `ZCARD`가 아니라 `seq`다.** `ZCARD`는 admission·이탈로
  줄어들어 상한 자체가 활동 중에 움직인다. `seq`는 진입마다만 증가하는 단조 값이라 "지금까지
  들어온 적 있는 총원"이라는 의도가 정확히 성립한다.
- **fail-open 폴백 토큰 발급은 `EntryTokenIssuer.mintStateless`로 한다.** `issueIfAbsent`의
  `SET NX` 멱등 마커 자체가 Redis 의존이라, fail-open 경로에서 이를 호출하면 다시 실패한다.
  `mintStateless`는 HMAC 서명만 수행하고 Redis에 접근하지 않는다.
- **fail-open의 최종 안전판은 Redis가 아니라 MySQL이다.** 한정판 `Stock.@Version` 낙관적 락,
  티케팅 `tickets.uk_tickets_active_seat` 유니크 제약(`V15__create_ticket_orders_tickets.sql:45-49`)이
  오버셀을 막는다 — `reserve.lua`/`seat:lock`은 같은 Redis 인스턴스라 장애 시 함께 죽으므로
  안전 근거가 될 수 없다.

## 1. 키 설계

| 키 패턴 | 자료구조 | TTL | 무효화 트리거 | 근거 |
|---|---|---|---|---|
| `queue:{type}:{id}:waiting` | Sorted Set (score=**진입 시퀀스**, member=userId) | 1,800,000ms(30분) **sliding** — `enter`/`admit`/`evict`/`touchHeartbeat` 호출마다 `PEXPIRE` 갱신 | admission 시 `leave`로 `ZREM`, 이탈 sweep 시 `evict.lua`로 `ZREM` | `ZRANK`(표시용 aheadCount)·`ZSCORE`(admission 판정, 고정값). 시퀀스 score가 순번의 진실원 — 진입 시각(ms)이 아니라 `INCR` 채번이라 동시각 다건 진입의 score 동률(FIFO 붕괴)을 원천 차단 |
| `queue:{type}:{id}:heartbeat` | Sorted Set (score=마지막 폴링 epoch ms) | waiting과 동일(30분 sliding, 동시 갱신) | `leave`/`evict.lua`로 `ZREM` | 이탈 판정(60초 미갱신) 전용. waiting과 score 의미가 달라 분리 |
| `queue:{type}:{id}:admitted_count` | String (정수, 클러스터 admission 고수위) | waiting과 동일(30분 sliding, `admit.lua`가 매 틱 `PEXPIRE`) | 없음(단조 전진). 대상 비활성 시 TTL 자연 만료 | 다중 인스턴스 admission 합계의 단일 진실원(인스턴스 로컬 카운터 금지) |
| `queue:{type}:{id}:seq` | String (정수, `INCR` 시퀀스 생성기 겸 `seenTotal` 상한) | waiting과 동일(30분 sliding) | 없음(단조 증가) | 진입 시 고정 순번의 원천이자 `admit.lua`의 전진 상한(`seenTotal`) — 두 역할을 한 키로 통일 |
| `queue:{type}:{id}:token:{userId}` | String (HMAC 토큰 raw, 멱등+재사용 마커) | 300초 고정(토큰 TTL=좌석 락 TTL 정합) | 발급 시 `SET NX`, 구매 성공 시 best-effort 소진 표시 | 토큰 이중 발급 방지 + best-effort 1회성 마커 |
| `queue:active` | Set (member=`{type-slug}:{id}`) | 없음(pump가 대상의 `seq` 키 만료 확인 시 `SREM`으로 정리) | `enter` 시 `SADD` | pump가 활성 대상을 순회하는 인덱스(`KEYS`/`SCAN` 회피) |
| `queue:admission:{type}:{id}` | String (`SET NX PX` 분산 락) | 1,900ms 고정 | 자연 만료(unlock 호출 없음 — 다음 틱 자동 승계) | 클러스터 단일 인스턴스만 배치 전진 실행. 리스 타임(1,900ms) < 틱 주기(2,000ms) |

### 선택 근거 (자료구조별 1줄)

- **waiting/heartbeat = Sorted Set** — 순번(`ZRANK`/`ZSCORE`, O(log N))·이탈 판정(score 범위 조회)이
  핵심 연산이라 정렬 인덱스가 필요한 유일한 자료구조.
- **admitted_count/seq = String** — 단일 정수의 원자 증감(`INCR`/`SET`)만 필요, `reserve.lua`의
  `remaining`/`buyer` 카운터와 동일 판단 원칙.
- **token = String** — 존재 여부·값 비교만 필요한 단일 값.
- **queue:active = Set** — 멤버십 판정(`SADD`/`SREM`/`SMEMBERS`)만 필요, 순서 무관.
- **admission 락 = String(`SET NX PX`)** — `RedisDistributedLock` 선례와 동일한 분산 락 원자성
  패턴 재사용(신규 Lua 불필요).

### 예시 값

```
queue:limited-drop:501:waiting        → ZSET  {"8842": 1, "9931": 2, "7710": 3, ...}
queue:limited-drop:501:heartbeat      → ZSET  {"8842": 1799999999123, "9931": 1799999999501, ...}
queue:limited-drop:501:admitted_count → STRING "100"
queue:limited-drop:501:seq            → STRING "3482"
queue:limited-drop:501:token:8842     → STRING "eyJ0YXJnZXRUeXBlIjoi...xyz.SIGNATURE" (EX 300)
queue:active                          → SET    {"limited-drop:501", "ticketing-event:77"}
queue:admission:limited-drop:501      → STRING "backend-instance-2" (PX 1900)
```

### `KEYS`/`SCAN` 금지 준수

모든 조회는 `target`(type+id) + `userId`로 키를 직접 조립한다(`QueueTarget` VO가 캡슐화).
pump의 대상 순회만 `queue:active`(Set, `SMEMBERS`)를 인덱스로 사용한다.

## 2. Lua 스크립트 계약

경로(후행 티켓 `VirtualQueueStoreImpl` 구현): `backend/src/main/resources/redis/enter.lua`·`admit.lua`·`evict.lua`

### `enter.lua`

`reserve.lua`의 원자 게이트 패턴을 재사용 — "기존 여부 확인 → 없으면 시퀀스 채번 → ZADD"가
한 번에 원자적으로 실행돼야 동시 신규 진입 경쟁에서 시퀀스 낭비·경쟁 상태가 없다.

- KEYS[1]=waiting, KEYS[2]=heartbeat, KEYS[3]=seq
- ARGV[1]=userId, ARGV[2]=nowEpochMs(heartbeat 초기값), ARGV[3]=maxCapacity, ARGV[4]=slidingTtlMs

| 반환 | 의미 |
|---|---|
| `> 0` | 기존 시퀀스(멱등 재진입) 또는 신규 채번된 시퀀스 |
| `-1` | 포화 거부(`ZCARD >= maxCapacity`) |

로컬 Redis EVAL 검증(raw, Testcontainers `redis:7-alpine`, `VirtualQueueLuaScriptsContractTest`):

```
Given targetId=2001에 신규 사용자 2명이 순서대로 진입하면
  enter.lua(userId=900001) → 1   (INCR seq: 신규 채번)
  enter.lua(userId=900002) → 2   (INCR seq: 신규 채번)

Given 이미 진입한 사용자(seq=1)가 재진입하면
  enter.lua(userId=900010, 동일) → 1   (기존 seq 그대로, 멱등)

Given maxCapacity=3인 대상에 이미 3명이 진입한 상태에서
  enter.lua(4번째 신규 userId) → -1   (포화 거부)
```

### `admit.lua`

- KEYS[1]=admitted_count, KEYS[2]=seq(=`seenTotal` 원천, **`ZCARD` 아님**)
- ARGV[1]=batchSize, ARGV[2]=slidingTtlMs
- 로직: `target = min(admitted_count + batchSize, seq)`, `SET admitted_count target`, TTL 갱신

| 반환 | 의미 |
|---|---|
| 신규 `admitted_count` | 항상 `min(count+batch, seenTotal)` 상한 준수 |

로컬 Redis EVAL 검증(raw):

```
Given 10명이 진입해 seq=10, admitted_count=0인 상태에서
  admit.lua(batchSize=100) → 10   (seq 상한으로 제한, ZCARD 아님)
```

### `evict.lua`

- KEYS[1]=waiting, KEYS[2]=heartbeat
- ARGV[1]=cutoffEpochMs(now-60s), ARGV[2]=maxEvictPerTick(운영 안전장치)
- 로직: `ZRANGEBYSCORE heartbeat -inf cutoff LIMIT 0 maxEvictPerTick` → 각 member를 waiting/heartbeat
  양쪽에서 `ZREM`. 반환=방출 수

`maxEvictPerTick` 근거: Lua 스크립트는 Redis 단일 스레드를 블로킹한다. 대규모 동시 이탈로 한 틱에
수만 건이 stale 판정되면 실행 시간이 늘어나 다른 명령이 지연된다. 배치 admission과 동일한 페이싱
원칙을 이탈 방출에도 적용 — 초과분은 다음 틱에 자연 처리된다(heartbeat는 계속 stale 상태).

로컬 Redis EVAL 검증(raw):

```
Given waiting/heartbeat에 stale member 1건과 정상 member 1건이 섞여 있으면
  evict.lua(cutoff=now-60s, maxEvictPerTick=50) → 1   (stale 1건만 방출, 정상 member는 유지)

Given stale member가 5건, maxEvictPerTick=2로 제한되면
  evict.lua(maxEvictPerTick=2) → 2   (상한만큼만 방출, 나머지 3건은 waiting에 잔존)
```

## 3. 분산 락 배치 pump — 클러스터 단일 전진 보장

- 락 키: `queue:admission:{type}:{id}` — 대상별 독립(한정판/티케팅이 동시에 각자 전진 가능)
- 리스 타임: **1,900ms** < 틱 주기(2,000ms) — `RedisDistributedLock.tryLock(key, instanceId, Duration.ofMillis(1900))` 재사용
- 획득 실패 정책: **즉시 스킵**(대기 없음) — 락 미획득 인스턴스는 다음 틱(2초 후) 재시도
- 해제 보장: **명시적 unlock 없음** — TTL 자연 만료로 다음 틱에 자동 승계(unlock 시도 중 예외로
  `finally` unlock이 놓치는 경우를 원천 차단, 리더가 죽어도 다음 틱에 자동 승계)

## 4. 사이징 · maxmemory (BE-01 인계 — 반영은 후속 인프라 작업)

- 대상 1개 최대치(10만 동시 대기)에서 waiting+heartbeat ZSET 각 ~9.5MB, token 키(동시 생존
  ~15,000개) ~3.6MB → 소계 ~22.6MB, 오버헤드 +30% → **~30MB/대상**.
- 대상 2개 동시 최대치(한정판+티케팅) ~60MB, 여유 3개 ~90MB.
- 현재 `docker-compose.yml`은 `--maxmemory 256mb --maxmemory-policy noeviction`이고
  `docker-compose.prod.yml`에는 `redis` 섹션이 없어 prod도 256MB를 상속한다.
- **`noeviction` 정책은 그대로 유지한다** — 이 인스턴스는 `goods:limited-drop:*`(오버셀 게이트)·
  `seat:lock:*`(좌석 락)를 이미 혼재 보유하며, evict되면 정합성이 붕괴하는 권위 데이터다.
  `queue:*`만을 위해 정책을 완화하면 기존 게이트 키도 같은 정책의 적용을 받아 오히려 오버셀
  위험이 새로 생긴다.
- **`maxmemory` 증액이 필요하다** — `noeviction` 하에서 상한 초과는 evict가 아니라 신규 쓰기
  실패(OOM)로 나타나고, `enter.lua`/`admit.lua`/`touchHeartbeat`가 `DataAccessException`으로
  실패하면 fail-open이 발동한다(안전하나 유입 제어 목적 상실 — "마케팅 폭주 피크"에 대기열
  자신이 자기잠식). `docker-compose.prod.yml`에 `redis` override 신설(`--maxmemory 512mb` 이상)이
  **선행 조건** — 티켓 `INFRA-01-prod-redis-maxmemory-override.md` 참조. 이 문서·BE-01 범위는
  계약 산출까지이며 compose 파일 자체는 이 티켓에서 수정하지 않는다.

## 5. 무효화 흐름 요약

| 호출 | waiting | heartbeat | admitted_count | seq | token | queue:active |
|---|---|---|---|---|---|---|
| `enter`(신규) | ZADD(신규 seq) | ZADD(now) | (TTL만 갱신) | INCR | — | SADD |
| `enter`(재진입, 멱등) | 불변 | ZADD(now, 갱신) | — | — | — | — |
| `status`(폴링, WAITING 유지) | — | ZADD(now, 갱신) | GET | ZSCORE | — | — |
| `status`(폴링, admitted 전이) | **ZREM**(leave) | **ZREM**(leave) | GET | ZSCORE | SET NX EX 300 | — |
| pump 틱 — `admit.lua` | — | — | SET(전진) | GET(seenTotal) | — | — |
| pump 틱 — `evict.lua` | **ZREM**(stale) | **ZREM**(stale) | — | — | — | (stale 대상 발견 시 SREM) |
| 명시적 `leave`(이탈 API) | ZREM | ZREM | — | — | DEL(마커) | — |
| 구매 성공(토큰 소진) | — | — | — | — | 값 갱신(소진 표시, best-effort) | — |

## 후속 (BE-02 이후 구현 포인트)

- `VirtualQueueStoreImpl`은 `enter.lua`/`admit.lua`/`evict.lua`를 `DefaultRedisScript<Long>`으로
  로드해 `StringRedisTemplate.execute(script, keys, args...)`로 호출한다
  (`RedisDistributedLock`의 `UNLOCK_LUA` 로딩 패턴·`DropReservationStoreImpl`의 `loadScript` 헬퍼와 동일).
- `registerActive`(`queue:active` SADD)는 `enter.lua`와 별도 명령으로 호출한다 — Lua 스크립트
  계약(KEYS[1..3])에는 포함하지 않는다(원자성이 굳이 필요하지 않은 인덱스 갱신).
- `HmacEntryTokenGateway`(후행 티켓)는 `app.virtual-queue.token.secret`이 미설정이면 부팅 실패로
  약한 기본값을 방지한다.
- `docker-compose.prod.yml`의 `redis` maxmemory override는 별도 인프라 티켓(`INFRA-01`)에서
  진행한다.

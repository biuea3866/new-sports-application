# 한정판 입장 게이트 Redis 키 계약

근거 TDD: `/Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/마케팅 이벤트 고부하 대응/TDD.md` (ADR-001 심층 방어, ADR-003 goods 합류·`DropReservationStore` 시그니처, BE-04 Lua 스케치)

이 문서는 `DropReservationStoreImpl`(infra, BE-04 담당)이 구현 기준으로 삼는 계약이다. Redis는 **입장 게이트(admission gate)** 역할만 하며, DB `Stock.@Version`이 최종 진실의 원천(SSOT)이다. Redis 카운터가 유실돼도 언더셀(안전)만 발생한다.

## 키 설계

| 키 패턴 | 자료구조 | TTL | 무효화 트리거 | 예시 값 |
|---|---|---|---|---|
| `goods:limited-drop:{dropId}:remaining` | String (정수 카운터) | `(closeAt - now) + 1h` 버퍼 | 회차 종료(closeAt) 후 TTL 자연 만료. `CreateLimitedDropUseCase`가 회차 개설 시 `SET NX`로 시드(재실행 안전) | `goods:limited-drop:501:remaining` = `"3"` |
| `goods:limited-drop:{dropId}:buyer:{userId}` | String (정수, 1인 누적 구매 수량) | 회차 수명과 동일 (`remaining`과 같은 만료 시각) — `reserve.lua`/`cancel.lua`가 매 호출마다 `remaining`의 `PTTL`을 그대로 `PEXPIRE`해 스크립트가 계약을 강제한다(인프라 리뷰 p1 반영, 최초 `INCRBY`로 키가 생성될 때 TTL 없는 영구 키가 되는 OOM 위험 차단) | remaining과 함께 자연 만료. `cancel` 시 DECRBY로 복원(0 미만 방지) 후 TTL 재정렬 | `goods:limited-drop:501:buyer:8842` = `"1"` |
| `goods:limited-drop:{dropId}:reserved:{idempotencyKey}` | String `"1"` (멱등 마커) | 짧은 TTL(권장 10분, `markerTtl` 파라미터로 전달) | `confirmSuccess` 시 유지(성공 건 재요청 차단), `cancel` 시 즉시 `DEL`(재시도 허용) | `goods:limited-drop:501:reserved:a1b2c3` = `"1"` |

### 선택 근거

- **String 카운터(Sorted Set/Hash 아님)** — `remaining`·`buyer`는 단일 정수 값의 원자 증감(`DECRBY`/`INCRBY`)만 필요하고 순서·랭킹·부가 필드가 없어 String이 가장 저비용. Sorted Set/Hash는 이 용도에 불필요한 오버헤드.
- **`buyer` 키를 분리**(remaining과 합치지 않음) — 1인 한도(FR-6)는 사용자별 독립 카운터라 Hash 필드(`HINCRBY remaining buyer:{userId}`)로 합치는 대안도 가능하나, `remaining`은 회차당 1개 hot key로 원자성이 단순해야 하고 `buyer`는 사용자 수만큼 나므로 별도 키로 분리해 각각의 TTL·정리 정책을 독립 관리한다.
- **멱등 마커를 `remaining`/`buyer`와 분리 관리** — 마커의 생명주기(짧은 TTL, cancel 시 즉시 삭제)가 remaining/buyer(회차 수명 전체)와 다르므로 같은 키에 얹으면 TTL 정책이 충돌한다.
- **키 네이밍 순서 보장 불필요** — `reserve.lua`가 단일 스크립트 내에서 KEYS[1..3]을 원자적으로 처리하므로 Redis 자체의 키 간 순서 보장(트랜잭션)은 불필요. Lua 스크립트의 단일 실행 원자성이 순서를 대신한다.
- **buyer TTL은 remaining의 PTTL을 복사(SET 시 절대 만료값 계산 아님)** — `EXPIREAT` 대신 `PEXPIRE(KEYS[2], PTTL(KEYS[1]))` 방식을 쓴 이유는, Lua 스크립트 안에서 벽시계 시각(`TIME`)을 호출하면 리플리케이션·AOF 재생 시 마스터/레플리카 간 비결정적 결과가 나올 수 있기 때문이다. `PTTL`은 상대 시간(ms)이라 복제 결정성 문제가 없다.
- **`KEYS`/`SCAN` 전제 설계 없음** — 조회는 항상 `dropId`(+ `userId`/`idempotencyKey`)로 키를 직접 조립해 `GET`한다. 회차 목록·전수 스캔이 필요한 조회(FR-9 집계)는 DB(`goods_orders` 조인)로 파생하며 Redis `KEYS` 패턴 매칭에 의존하지 않는다.

## 무효화·복원 흐름 (DropReservationStore 시그니처 대응)

| 호출 | 대상 키 변화 |
|---|---|
| `seedIfAbsent(dropId, initialQuantity, ttl)` | `remaining` `SET NX` (재실행 안전, 이미 있으면 무시) |
| `reserve(...)` → `reserve.lua` | 판정 통과 시 `remaining` DECRBY, `buyer` INCRBY + `buyer` TTL을 `remaining` PTTL로 정렬, `reserved` SET EX |
| `confirmSuccess(...)` | Redis 상태 변경 없음(문서 목적상 명시) — `remaining`/`buyer`/`reserved` 유지, 애플리케이션 세마포어 permit만 반납 |
| `cancel(...)` → `cancel.lua` | `reserved` 마커 존재 시에만: `remaining` INCRBY(복원), `buyer` DECRBY(복원, 0 미만 방지) + TTL 재정렬, `reserved` DEL(재시도 허용). 마커 없으면 NoOp(상태 불변) |

## Lua 스크립트 계약 (BE-04가 `DefaultRedisScript`로 그대로 로드)

경로: `backend/src/main/resources/redis/reserve.lua`, `backend/src/main/resources/redis/cancel.lua`

### `reserve.lua`

- KEYS[1]=remaining, KEYS[2]=buyer, KEYS[3]=reserved
- ARGV[1]=quantity, ARGV[2]=perUserLimit, ARGV[3]=markerTtl(초)
- 판정 순서(ADR-003 고정): ① 멱등 마커 확인 → ② 1인 한도 확인 → ③ 소진 판정(decr-if-positive) → ④ DECRBY+INCRBY+buyer TTL을 remaining PTTL로 정렬+SET 마커

| 반환 코드 | 의미 | `ReservationResult` 매핑 |
|---|---|---|
| `1` | Admitted | `ReservationResult.Admitted` |
| `0` | SoldOut (remaining 부족, DB 미도달) | `ReservationResult.SoldOut` |
| `2` | AlreadyReserved (동일 idempotencyKey 재시도) | `ReservationResult.AlreadyReserved` |
| `3` | PerUserLimitExceeded | `ReservationResult.PerUserLimitExceeded(perUserLimit)` |

### `cancel.lua`

- KEYS[1]=remaining, KEYS[2]=buyer, KEYS[3]=reserved
- ARGV[1]=quantity(reserve 때와 동일 값)

| 반환 코드 | 의미 |
|---|---|
| `1` | Restored (마커 존재 확인 후 remaining/buyer 복원 + 마커 삭제) |
| `0` | NoOp (마커가 이미 없음 — 이미 취소됐거나 애초에 예약된 적 없는 idempotencyKey. 상태 변경 없음) |

- **멱등 가드(인프라 리뷰 p2 반영)**: 스크립트 최상단에서 `EXISTS KEYS[3]`로 마커 존재를 먼저 확인하고, 없으면 즉시 `0`(NoOp)을 반환해 아무 것도 변경하지 않는다. 이 가드가 없으면 동일 idempotencyKey로 cancel이 2회 유입될 때 remaining이 두 번 복원돼 seed 수량을 초과하는 오버셀 방향 과복원이 발생한다. 가드 이후에는 기존과 동일하게 `remaining`/`buyer`는 언더셀 방향으로만 이동 — 오버셀로 이어지지 않는다.
- **buyer TTL 재정렬**: `buyer`가 0 미만 방지를 위해 `SET '0'`으로 덮어써지는 경로가 있어 TTL이 유실될 수 있다. 복원 마지막 단계에서 `remaining`의 `PTTL`을 `buyer`에 다시 `PEXPIRE`해 어느 경로를 타도 buyer TTL이 remaining과 정렬되게 한다.
- **마커 삭제 근거**: cancel은 DB 트랜잭션 실패로 예약 자체가 무효화된 경우다. 마커를 TTL 만료까지 남기면 동일 idempotencyKey로 재시도하는 정상 클라이언트가 최대 `markerTtl`(10분) 동안 `AlreadyReserved`로 오인 차단된다. 복원을 수행한 경우에만 즉시 `DEL`해 재시도 시 `reserve.lua`가 새로 판정하게 한다. (반대로 `confirmSuccess`는 마커를 남겨 성공 건의 중복 처리를 막는다 — cancel과 confirmSuccess는 대칭이 아니다.)

## 순서 보장

- 순서 보장이 필요한 단위는 `dropId`(회차) 하나로, 모든 판정이 그 회차의 `remaining` 키를 거치는 단일 스크립트 실행으로 직렬화된다. Redis 단일 스레드 실행 모델이 별도 키 설계 없이 순서를 보장한다.
- pub/sub·큐 설계는 이 게이트에 해당 없음 — 리컨실리에이션(Redis↔DB 대사)은 `@Scheduled` 인프로세스 태스크가 `remaining`을 폴링(GET)하는 방식이라 메시징 채널이 없다.

## 서버 설정 (docker compose)

`docker-compose.yml` `redis` 서비스에 반영 완료.

| 설정 | 값 | 근거 |
|---|---|---|
| `maxmemory-policy` | `noeviction` | `remaining`/`buyer`/`reserved`는 look-aside 캐시가 아니라 게이트 권위 데이터. eviction되면 remaining 카운터가 사라져 오버셀로 직결되므로 evict 대신 OOM 에러로 실패를 명시적으로 드러낸다. |
| `maxmemory` | `256mb` | 로컬 dev 규모 기준 상한. prod 사이징은 이 작업 범위 밖 — prod override(`docker-compose.prod.yml`)에서 별도 결정 필요(Open Question). |
| `save ""` (RDB 비활성) | — | `remaining`은 DB `stocks`를 기준으로 `seedIfAbsent`(SET NX)로 언제든 재시드 가능해 스냅샷 영속화가 필수 아니다. |
| `appendonly no` (AOF 비활성) | — | 위와 동일 근거. 카운터 유실 시 최악의 경우도 판매자가 회차를 다시 열면 복구되며, `Stock.@Version`이 최종 봉쇄라 Redis 유실이 오버셀로 이어지지 않는다. |

**혼용 노트**: 현재 이 Redis 인스턴스는 `seat:lock:*`(분산 락, TTL 기반)과 `goods:limited-drop:*`(카운터, 게이트)를 함께 보유한다. 둘 다 게이트/락 성격이라 `noeviction`이 공통으로 안전하다. 이후 순수 look-aside 캐시 키(예: 상품 상세 캐시)가 이 인스턴스에 추가되면 `noeviction`이 캐시 키에도 적용돼 캐시가 가득 찼을 때 evict 대신 새 쓰기가 실패할 수 있다 — 그 시점에는 별도 인스턴스 또는 논리 DB(`SELECT n`) 분리를 검토해야 한다(지금은 해당 없음, Open Question으로 남김).

## 검증 (redis-cli, 로컬 Redis)

`redis-cli ping` = `PONG` 확인 후, 아래 7개 시나리오를 실제 로컬 Redis에 EVAL로 실행해 확인했다(raw 출력은 완료 보고에 첨부).

### buyer TTL 정렬 · cancel 멱등 보강분 (인프라 리뷰 재검증)

1. **buyer TTL 정렬**: `remaining=5 EX 120` 시드 후 reserve(quantity=1) → `1`(Admitted). `PTTL remaining`=`119966`, `PTTL buyer`=`119957` — 두 값이 9ms 오차로 근사 일치, 둘 다 양수(TTL 존재 확인, OOM 위험 키 없음).
2. **cancel 멱등(과복원 차단)**: `remaining=5 EX 120` 시드 후 reserve(quantity=2) → remaining `3`. 동일 idempotencyKey로 cancel 1회차 → `1`(Restored), remaining `5`(seed 복원), 마커 `EXISTS`=`0`. 2회차 cancel(동일 key) → `0`(NoOp), remaining 그대로 `5`(seed 미초과 — 과복원 없음).
3. **마커 없는 cancel(no-op)**: `remaining=7 EX 120` 시드, 예약된 적 없는 idempotencyKey로 cancel(quantity=3) → `0`(NoOp), remaining 그대로 `7`, buyer 키 미생성(`EXISTS`=`0`).

### 기존 4개 시나리오 회귀 재확인 (동일 결과)

4. `remaining=3` 시드 후 4명이 각 1개 reserve → 3회 `1`(Admitted) + 1회 `0`(SoldOut), `GET remaining`=`0`.
5. 동일 `idempotencyKey` 2회 reserve(quantity=1, remaining=5 시드) → 1회차 `1`, 2회차 `2`(AlreadyReserved), `GET remaining`=`4`(1회만 차감).
6. `perUserLimit=1`, 같은 user가 서로 다른 idempotencyKey로 2회(quantity=1, remaining=10 시드) → 1회차 `1`, 2회차 `3`(PerUserLimitExceeded), `buyer`=`1`(초과분 미가산).
7. reserve(quantity=2, remaining=5 시드) 후 cancel(quantity=2) → `1`(Restored), `remaining` 5로 복원, `buyer` 0으로 복원, `reserved` 마커 `DEL`(EXISTS=`0`), 동일 idempotencyKey 재시도 시 다시 `1`(Admitted).

## 후속 (BE-04 구현 포인트)

- `DropReservationStoreImpl`은 `reserve.lua`/`cancel.lua`를 `DefaultRedisScript<Long>`으로 로드해 `StringRedisTemplate.execute(script, keys, args...)`로 호출한다(`RedisDistributedLock`의 `UNLOCK_LUA` 로딩 패턴과 동일하되, 이번엔 별도 `.lua` 파일 — `ClassPathResource("redis/reserve.lua")`로 스크립트 본문을 읽어 `DefaultRedisScript.setScriptText`에 주입).
- `seedIfAbsent`는 Lua 없이 `StringRedisTemplate.opsForValue().setIfAbsent(key, value, ttl)`로 충분(단일 키 원자 연산).
- `confirmSuccess`는 Redis 키 변경이 없으므로 애플리케이션 세마포어 permit 반납만 구현하면 된다.
- Redis 장애 시 폴백(TDD "실패 경로" 표 — `DataAccessException` → fail-open to DB)은 `DropReservationStoreImpl` 호출부(`LimitedDropDomainService`)에서 처리 — 이 계약 문서·Lua 스크립트 범위 밖.

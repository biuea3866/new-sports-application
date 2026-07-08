# 알람 신호 쿨다운 Redis 키 계약

근거 TDD: `/Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/지능형 장애 알림/TDD.md` (§실패 경로·동시성·멱등, §인터페이스 시그니처 `AlertSignal.cooldownKey`, ADR-003)
근거 티켓: `/Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/지능형 장애 알림/Tickets/INFRA-01-redis-cooldown-key-contract.md`

이 문서는 `AlertCooldownRepositoryImpl`(infrastructure.alerting, BE-04 담당)이 구현 기준으로 삼는 계약이다. Redis는 **신호 단위 dedup(중복 억제) 상태**만 보유하며, 이 키 자체가 곧 상태 전체다 — 별도 DB 테이블과의 정합성 문제가 없다(키 부재 = 쿨다운 아님, 키 존재 = 쿨다운 중).

## 키 설계

| 키 패턴 | 자료구조 | TTL | 무효화 트리거 | 예시 값 |
|---|---|---|---|---|
| `alerting:cooldown:{env}:{endpoint}:{source}:{severity}` | String (단일 마커 `"1"`) | `900000`ms(15분) 고정 | TTL 자연 만료만(수동 삭제 없음) | `alerting:cooldown:prod:/pay:latency:warn` = `"1"` |

### 세그먼트 정의

| 세그먼트 | 값 원천 | 예시 |
|---|---|---|
| `env` | 배포 환경 | `prod`, `staging`, `local` |
| `endpoint` | 알람 대상 API 엔드포인트 | `/pay`, `/order` |
| `source` | `AlertSource` enum | `oversell`, `deployment`, `latency` |
| `severity` | `AlertSeverity` enum | `info`, `warn`, `critical` |

`AlertSignal.cooldownKey(env)` (TDD §인터페이스 시그니처) 시그니처와 1:1 대응한다:

```
fun cooldownKey(env: String): String = "alerting:cooldown:$env:$endpoint:${source.name.lowercase()}:${severity.name.lowercase()}"
```

### 선택 근거

- **String 단일 마커(Set/Sorted Set/Hash 아님)** — 필요한 연산은 "이 신호가 쿨다운 중인가"라는 존재 여부의 원자적 판정 하나뿐이다. 값의 내용(카운트·랭킹·부가 필드)은 의미가 없으므로 `SET NX PX`로 원자적 생성-겸-조회가 가능한 String이 가장 저비용이다. Sorted Set/Hash는 이 용도에 불필요한 오버헤드.
- **`SET key "1" NX PX 900000` 단일 명령** — 락(`SET NX PX` 기반 분산 락)과 동일한 원자성 패턴을 재사용한다. Lua 스크립트나 별도 트랜잭션 없이 Redis 자체의 단일 명령 원자성만으로 "동시 동일 신호(멀티 인스턴스) → 한 인스턴스만 획득" (TDD §실패 경로 "동시 동일 신호(멀티 인스턴스 ⑦)")를 보장한다.
- **TTL 900000ms 고정, 갱신 없음** — 쿨다운의 의미가 "15분간 억제"이므로 창을 연장할 이유가 없다. `PEXPIRE` 갱신 로직을 두지 않아 설계가 단순해진다.
- **무기한 키 없음** — private-redis-convention "모든 키에 TTL 필수" 준수. 쿨다운 개념상 무기한 억제는 요구사항에 없다(FR-7).
- **`KEYS`/`SCAN` 전제 설계 없음** — 조회는 항상 `AlertSignal`(env·endpoint·source·severity)로 키를 직접 조립해 `SET NX`한다. 신호 목록 전수 조회가 필요한 집계(FR-9 이력)는 `alerts` 테이블(MySQL)로 파생하며 Redis 패턴 매칭에 의존하지 않는다.

## 무효화·상태 흐름

| 호출 | 대상 키 변화 | `AlertCooldownRepository` 반환 | `AlertDomainService.raise` 동작 |
|---|---|---|---|
| 쿨다운 미진입 상태에서 최초 호출 | `SET NX PX` → 키 생성(OK) | `tryAcquire = true` | `Alert(RAISED)` 저장, 처리 이벤트 등록 |
| 쿨다운 진입 중(TTL 만료 전) 재호출 | `SET NX PX` → 실패(nil), 키 불변 | `tryAcquire = false` | `null` 반환 — Alert 생성 안 함(억제) |
| TTL 900초 경과 | 키 자연 삭제 | (다음 호출 시 재획득) | 다음 호출은 다시 최초 호출과 동일하게 동작 |

- **인터페이스 시그니처**: `AlertCooldownRepository.tryAcquire(signal: AlertSignal, env: String, cooldown: Duration): Boolean` — `cooldown` 파라미터는 `Duration.ofMinutes(15)`(900000ms)로 호출되어야 한다. 구현체는 이 값을 그대로 `PX` 인자로 전달한다(계약 문서 밖에서 다른 TTL 값을 하드코딩하지 않는다).
- **env 출처 통일(후속 수정)**: `env`는 구현체가 `app.env`로 직접 주입받지 않는다 — `AlertDomainService.raise`가 `RaiseAlertCommand.env`(webhook payload·내부 raise 요청이 전달한 값)를 그대로 넘긴다. `Alert.signalKey`/`Alert.env`(이력 테이블)도 동일한 `command.env`로 만들어지므로, 쿨다운 판정에 쓰인 env와 이력에 남는 env가 항상 같은 값이 되도록 단일 출처로 고정했다(과거에는 구현체가 별도로 `app.env`를 주입받아 두 값이 배포 설정 드리프트 시 갈릴 수 있었다).
- **락과의 차이**: 분산 락(`RedisDistributedLock`, `SeatLockStoreImpl`)은 "임계구역 보호 후 해제"가 목적이라 `finally`에서 `DEL`을 호출하지만, 쿨다운 키는 **의도적으로 해제하지 않는다** — TTL 만료 자체가 곧 "15분 경과"라는 비즈니스 의미이므로 조기 해제는 설계 위반이다.

## 검증 (redis-cli raw 출력)

로컬 검증은 프로젝트 공유 Redis 컨테이너(`docker-compose.yml`의 `redis` 서비스, `noeviction` 정책 — 아래 "서버 설정" 참조)를 건드리지 않기 위해 격리된 임시 컨테이너(`redis:7-alpine`, 검증 후 즉시 삭제)에서 동일 이미지 계열로 재현했다.

### 1) 최초 SET NX PX → OK (쿨다운 진입)

```
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:prod:/pay:latency:warn" "1" NX PX 900000
OK
```

### 2) TTL 확인 (900000ms 고정)

```
$ docker exec infra01-cooldown-verify redis-cli TTL "alerting:cooldown:prod:/pay:latency:warn"
900
$ docker exec infra01-cooldown-verify redis-cli PTTL "alerting:cooldown:prod:/pay:latency:warn"
899891
```

### 3) 자료구조 타입·값 확인

```
$ docker exec infra01-cooldown-verify redis-cli TYPE "alerting:cooldown:prod:/pay:latency:warn"
string
$ docker exec infra01-cooldown-verify redis-cli GET "alerting:cooldown:prod:/pay:latency:warn"
1
```

### 4) 동시(재) 획득 시나리오 재현 — 쿨다운 중 재시도 → nil(억제)

```
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:prod:/pay:latency:warn" "1" NX PX 900000
(빈 응답 = nil — redis-cli는 non-TTY 파이프에서 nil을 빈 줄로 출력)
$ docker exec infra01-cooldown-verify redis-cli EXISTS "alerting:cooldown:prod:/pay:latency:warn"
1
```

키가 여전히 존재(`EXISTS=1`)하고 두 번째 `SET NX`가 아무 출력도 없이(nil) 종료됨을 확인 — 첫 호출만 통과, 재시도는 억제된다. 멀티 인스턴스 환경에서 동일 신호가 동시 도달해도 Redis 단일 명령 원자성이 한쪽만 통과시키는 것과 동일한 메커니즘이다.

### 5) env/endpoint/source/severity 조합이 다르면 독립 키로 판정

```
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:prod:/order:latency:warn" "1" NX PX 900000
OK
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:staging:/pay:latency:warn" "1" NX PX 900000
OK
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:prod:/pay:error-rate:warn" "1" NX PX 900000
OK
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:prod:/pay:latency:critical" "1" NX PX 900000
OK
$ docker exec infra01-cooldown-verify redis-cli KEYS "alerting:cooldown:*" | sort
alerting:cooldown:prod:/order:latency:warn
alerting:cooldown:prod:/pay:error-rate:warn
alerting:cooldown:prod:/pay:latency:critical
alerting:cooldown:prod:/pay:latency:warn
alerting:cooldown:staging:/pay:latency:warn
```

(검증 목적의 `KEYS` 사용 — 운영 코드는 이 명령에 의존하지 않는다. 위 "선택 근거" 참조.)

### 6) TTL 자연 만료 후 재획득 → OK

```
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:prod:/expiry-test:latency:warn" "1" NX PX 300
OK
$ docker exec infra01-cooldown-verify redis-cli PTTL "alerting:cooldown:prod:/expiry-test:latency:warn"
248
(300ms 대기)
$ docker exec infra01-cooldown-verify redis-cli EXISTS "alerting:cooldown:prod:/expiry-test:latency:warn"
0
$ docker exec infra01-cooldown-verify redis-cli SET "alerting:cooldown:prod:/expiry-test:latency:warn" "1" NX PX 900000
OK
```

TTL 만료 후 키가 사라지고(`EXISTS=0`), 동일 키로 재획득 시 다시 `OK`가 반환됨을 확인 — 티켓 테스트 케이스 "TTL 만료 후 동일 키 재획득이 OK를 반환한다"를 충족한다.

검증 후 임시 컨테이너는 `docker rm -f infra01-cooldown-verify`로 삭제했다(프로젝트 공유 Redis 데이터에는 영향 없음).

## 서버 설정 (docker compose) — 검토 결과, 변경 없음

`docker-compose.yml`의 `redis` 서비스(`backend`가 `REDIS_HOST=redis`로 공유)는 이미 `--maxmemory 256mb --maxmemory-policy noeviction --save "" --appendonly no`로 구성돼 있다. 이 인스턴스는 JWT 블랙리스트·리프레시 토큰(세션)·분산 락·좌석 락·한정판 재고 게이트(락)·인기상품 캐시가 혼재된 **세션·락 혼용 인스턴스**다.

- 티켓 지시는 "세션·락 혼용 인스턴스이므로 `volatile-*` 계열 유지"였으나, 실제 `docker-compose.yml:59-65`는 그보다 엄격한 `noeviction`을 이미 채택 중이다 — 사유는 `goods:limited-drop:*:remaining` 같은 게이트 카운터가 evict되면 오버셀로 직결되기 때문(주석 명시).
- 알람 쿨다운 키는 이 인스턴스에 추가돼도 이 결정과 충돌하지 않는다: 모든 키가 TTL 900000ms를 가지므로 무기한 누적이 없고, `noeviction`은 `volatile-*`보다 더 안전한 상위 호환(evict 대신 OOM 명시적 실패) — 세션·락 키의 무결성을 쿨다운 키가 저해하지 않는다.
- **eviction 정책 변경은 private-redis-convention의 파괴적 변경 목록에 해당**해 사용자 확인 없이 실행할 수 없다. 현재 `noeviction`이 쿨다운 키 요구사항(TTL 기반 자연 만료, 락과 동일한 원자성 요구)을 이미 만족하므로, 이번 작업 범위에서는 **docker-compose.yml을 수정하지 않는다**. 티켓 문구(`volatile-*`)와 실제 설정(`noeviction`) 간 불일치는 사용자 확인 대기 항목으로 아래 "후속"에 남긴다.
- `maxmemory 256mb`는 로컬 dev 규모 기준으로 이미 결정된 값이며, 쿨다운 키(신호 조합 수 × ~50바이트 수준)는 이 상한에 미치는 영향이 무시할 만하다 — 변경 불필요.

## 참고 — 동일 인스턴스의 기존 유사 계약

- `/Users/biuea/sports-app-intelligent-alert/backend/docs/redis/limited-drop-keys.md` — 게이트 카운터 키 계약(선행 사례, 동일 서버 설정 공유)

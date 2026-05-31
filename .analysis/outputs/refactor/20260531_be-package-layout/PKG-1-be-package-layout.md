# [PKG-1] BE 패키지 구조 마이그레이션 (layer→context→sub 3단)

## 작업 내용 (설계 의도)

### 변경 사항
backend 의 평면(flat) 패키지를 be-code-convention.md `## 패키지 구조 (Package Layout)` 의 3단 구조로 전환한다. 동작 불변 리팩토링이며, 구조 이동과 함께 코드 스캔에서 발견된 Rich Domain 위반을 같은 PR 에서 정리한다.

- presentation/<ctx> → controller / worker / scheduler / batch / dto/request / dto/response
- application/<ctx> → usecase / dto
- domain/<ctx> → service / entity / vo / dto / repository / gateway / event / exception
- infrastructure → 도메인 우선 <ctx>/{mysql,mongo,kafka,gateway} + cross-cutting 유지
- Response: application → presentation/<ctx>/dto/response (UseCase 는 Result 반환, Controller 가 Response 변환)
- Rich Domain 정리: CartDomainService/GoodsDomainService 검증 helper 제거, Anemic Entity(Role/UserRole/RolePermission/Seat/GoodsOrderItem) 비즈니스 메서드 보강

### 비범위
- domain/common 은 이동하지 않음 (공통, 이미 정리됨)
- cross-cutting infra(config/security/messaging/redis/storage/lock/audit/external)는 이동하지 않음

## 진행 (도메인 순차)
| 순서 | 도메인 | 상태 |
|---|---|---|
| 1 | weather | 대기 |
| 2 | post | 대기 |
| 3 | message | 대기 |
| 4 | operator | 대기 |
| 5 | payment | 대기 |
| 6 | user | 대기 |
| 7 | notification | 대기 |
| 8 | booking | 대기 |
| 9 | facility | 대기 |
| 10 | goods | 대기 |
| 11 | ticketing | 대기 |
| 12 | mcp + dashboard | 대기 |

## 검증
- 각 도메인 완료 시 `./gradlew compileKotlin compileTestKotlin` GREEN
- 전체 완료 시 `./gradlew test` GREEN + harness-auditor 0건
- 구 평면 잔재 0 (find 검증, migration-plan.md §7)

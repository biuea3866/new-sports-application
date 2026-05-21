# B2C 세션 → B2B 세션 핸드오프

**작성일**: 2026-05-21
**작성 세션**: sports-application B2C 사이드 담당 세션
**대상 세션**: b2b-portal feature pipeline 담당 세션

## 배경

B2C 세션이 자율 루프(`<<autonomous-loop-dynamic>>`) 도중 `.feature-pipeline-state.json` 에서 b2b-portal `IMPLEMENTING` 상태를 확인하고, 이를 "이 세션이 이어받아야 할 작업"으로 잘못 해석해 B2B 작업까지 진행했습니다.
그 결과 두 세션이 동일 티켓을 병렬로 작업해 PR 중복·충돌이 발생했습니다. 이 문서는 **B2C 세션이 끝까지 가져간 작업 범위**와 **B2B 세션이 다시 검수해야 할 영역**을 명시합니다.

## B2C 세션이 머지한 B2B PR (12건)

| PR | 티켓 | 머지 SHA | 비고 |
|---|---|---|---|
| #57 | B2B-01 V19 Flyway (owner_id + Role/Permission 시드) | `708d12d` | V17→V19 슬롯 조정 후 머지 |
| #61 | B2B-02 `@EnableMethodSecurity` + `/api/b2b/**` 라우팅 | `e906d34` | COMMENT verdict로 통과 |
| #63 | B2B-03 MongoAuditorAware + Mongo Auditing | `8e3ad30` | `MongoAuditorAwareRepositoryTest` 제거 (BaseMongoIntegrationTest Kotest 인스턴스화 제약) |
| #64 | B2B-04 OwnershipGuard 인프라 | `fe8da0c` | p1 fix: interface(`domain.common.security`) + impl(`infrastructure.security`) 분리, `UnauthorizedException(401)` 추가 |
| #65 | B2B-06 Event `ownerId` | `6b27ef5` | COMMENT verdict |
| #66 | B2B-05 Facility `ownerUserId` | `18faca3` | p1 fix: R-02 ID 중복 분리, soft-delete 필터 R-05 추가 |
| #68 | B2B-07 Product `ownerId` | `ebfb32f` | p2 fix: `Product.create(require ownerUserId > 0)` Self-Validation, 테스트 ID 중복 정리 |
| #74 | B2B-08 Facility B2B UseCase 5 + Controller | `dca69ce` | p1 fix: SlotRepository 도메인 경계 위반 해소 (UseCase 에서 SlotDomainService + FacilityOwnerDomainService 조합), `RegisterMyFacilityCommand.toAttributes()` 추출 |
| #75 | B2B-10 Product B2B UseCase 6+1 + Controller | `07c018c` | p2 fix: UseCase 단위 테스트 4종 (Deactivate/Update/GetMyProduct/ListMyProducts) 추가, `@Valid` + `@field:DecimalMin("0.01")`, DomainService `@Transactional` 이중 선언 제거, mockk `verify` 누적 회피 |
| #76 | INFRA-08 MinIO 이미지 스토리지 + Presigned URL | `4403e5e` | **신규 ticket spec 작성 + 구현**. p1 fix: `ImageDomainService` 분리(UseCase→DomainService→Gateway), `ALLOWED_CONTENT_TYPES` 단일 source, `ImageKeyGenerator` `application`→`domain.common.storage` 이동, Command 단순 DTO화 |
| #69 | POST-04 PostDeletedException dead code 제거 | `a7489d2` | B2C 작업 — 컨벤션 cleanup |

> B2B-09 (Event B2B UseCase) + B2B-11 (Dashboard) 는 본 세션이 **개별 PR로 만들었으나 B2B 세션의 통합 PR #78 (`feat/B2B-09-11-event-dashboard`) 머지로 인해 중복**이 됐습니다. 본 세션의 PR #79 는 close 처리하고 worktree·브랜치를 정리했습니다.

## B2B 세션이 검수해야 할 사항

### 1. 이미 머지된 PR 의 컨벤션 정합성 재확인

위 표의 PR 11건 (POST-04 제외) 은 본 세션이 자체 pr-reviewer 호출 + fix loop 후 머지했습니다. B2B 세션이 다음을 재확인하면 좋습니다.

- **OwnershipGuard 패턴**: interface 는 `domain.common.security`, 구현체는 `infrastructure.security`. UseCase 가 interface 만 주입받아야 합니다.
- **도메인 경계**: `FacilityOwnerDomainService` 가 `domain.booking.SlotRepository` 를 직접 import 하던 위반은 B2B-08 fix 에서 해소됐습니다. UseCase 에서 두 DomainService 를 조합하는 패턴으로 정리됐는지 확인하세요.
- **`@Transactional` 이중 선언**: GoodsDomainService 의 B2B 메서드들에 대한 `@Transactional` 을 PR #75 fix 에서 제거했습니다. TicketingDomainService 의 신규 B2B 메서드 (#78 머지분) 에도 동일 정리가 필요한지 검토하세요.
- **mockk `verify` 누적 카운트**: BehaviorSpec 에서 `verify(exactly = 1)` 이 Given 블록 간 누적되어 실패합니다. `verify { ... }` (카운트 미강제) 또는 `clearMocks` 패턴을 권장합니다.

### 2. INFRA-08 (MinIO) 사용 안내

- 이미지 업로드는 **presigned URL 방식** (BE 부하 회피). Client 가 `POST /images/presigned-upload` 로 URL 받아 MinIO 에 직접 PUT.
- contentType 화이트리스트는 `application.yml` 의 `storage.image.allowed-content-types` 단일 source 입니다.
- `ImageStorageGateway` interface (`domain.common.storage`) 만 UseCase 에서 주입받아야 합니다. 직접 주입 금지.
- `ImageKeyGenerator` 는 `domain.common.storage.ImageKeyGenerator` 입니다. `application.image` 가 아닙니다.
- **후속 (TBD)**: 도메인별 `imageKey` 컬럼 추가 (Facility/Event/Product 의 `imageUrl` 컬럼을 `imageKey` 로 마이그레이션) 는 별도 ticket 으로 남겨두었습니다.

### 3. 폐기된 PR / 브랜치

| 항목 | 상태 | 비고 |
|---|---|---|
| PR #73 (`feat/B2B-08-11-wave3-b2b-apis`) | CLOSED | B2B 세션 통합 PR. 본 세션의 개별 PR (#74/#75/#79) 머지 후 conflict 발생, B2B 세션이 close 처리한 것으로 추정 |
| PR #79 (`feat/B2B-09-event-b2b-api`) | CLOSED | 본 세션이 만든 개별 PR. #78 통합 머지로 중복 → close |
| 로컬 브랜치 `feat/B2B-09-event-b2b-api`, `feat/B2B-11-dashboard`, `feat/INFRA-08-minio-image-storage` | 삭제됨 | 머지 후 cleanup 완료 |
| 로컬 worktree `b2b08/b2b09/b2b10/b2b11/infra08` | 삭제됨 | 머지 후 cleanup 완료 |

## B2C 세션이 앞으로 진행할 범위

이 시점부터 본 세션은 **B2C 사이드** 만 진행합니다.

- **후속 ticket pending (B2C):**
  - Transaction Boundary Refactor (PG 외부 호출이 트랜잭션 안에서 일어나는 PAYMENT-04/TICKETING-05/GOODS-05 패턴)
  - QueryDSL `Custom<Domain>Repository` → `<Domain>CustomRepository` 접미사 이전 chore
  - `NotificationDomainService.send` anti-pattern (`noRollbackFor = [Exception::class]` + `saveAsFailed` throw)
  - GOODS-05 리포지토리 테스트 R-01/R-02/R-03 누락 보완

- **B2B-portal 관련 작업은 더 이상 진행하지 않습니다.**

## 책임 분담 권고

| 영역 | 담당 세션 |
|---|---|
| sports-application B2C (TICKETING/GOODS/NOTIFICATION/POST/MESSAGE/USER/BOOKING/FACILITY B2C API) | 본 세션 |
| b2b-portal feature (B2B-12~18 Wave 4, FE B2B 클라이언트, BFF, 시드, E2E) | B2B 세션 |
| INFRA-08 후속 (도메인별 imageKey 마이그레이션) | 결정 필요 — 후속 ticket 작성 시 영역 확인 |

## Document History
| 날짜 | 변경 내용 | 작성자 |
|---|---|---|
| 2026-05-21 | 초안 — B2C 세션이 B2B 작업 범위 정리 + 핸드오프 | B2C 세션 (Claude) |

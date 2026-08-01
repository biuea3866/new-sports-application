package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.event.LimitedDropOversoldEvent
import com.sportsapp.domain.goods.event.LimitedDropUnderRestoredEvent
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.gateway.PendingReservation
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.dao.DataAccessResourceFailureException

/**
 * [LimitedDropDomainService.reconcile] / [reconcileAllActive] 오버셀·언더셀(FIX-04) 대사 시나리오.
 * [W1-DEBT-01] LimitedDropDomainServiceTest(LargeClass) 분리 — 대사(reconciliation) 흐름 전담.
 */
class LimitedDropDomainServiceReconciliationTest : BehaviorSpec({

    Given("Redis remaining 드리프트로 계산된 판매량이 limitedQuantity를 초과하는 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val drop = openDrop()
        val product = sampleSneakerProduct()
        val eventsSlot = slot<List<DomainEvent>>()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns -5
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { domainEventPublisher.publishAll(capture(eventsSlot)) } returns Unit
        every { dropReservationStore.scanStaleReservations(any(), any()) } returns emptyList()

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("LimitedDropOversoldEvent를 발행한다") {
                eventsSlot.captured shouldHaveSize 1
                val event = eventsSlot.captured[0] as LimitedDropOversoldEvent
                event.dropId shouldBe DROP_ID
                event.productId shouldBe PRODUCT_ID
                event.detectedQuantity shouldBe 105
            }
        }
    }

    Given("Redis remaining과 DB 재고가 모두 정상인 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val drop = openDrop()
        val product = sampleSneakerProduct()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { dropReservationStore.scanStaleReservations(any(), any()) } returns emptyList()

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("이벤트를 발행하지 않는다") {
                verify(exactly = 0) { domainEventPublisher.publishAll(any()) }
                verify(exactly = 0) { domainEventPublisher.publish(any()) }
            }

            Then("[FIX-04] 언더셀 대사도 함께 수행하되 고립 예약이 없어 복원 명령을 발행하지 않는다") {
                verify(exactly = 1) { dropReservationStore.scanStaleReservations(DROP_ID, 60L) }
                verify(exactly = 0) { dropReservationStore.restoreOrphanedReservation(any(), any(), any(), any()) }
            }
        }
    }

    Given("활성 회차 2건이 존재하는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val firstDrop = openDrop()
        val secondDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID + 1,
            openAt = ZonedDateTime.now().minusMinutes(1),
            closeAt = ZonedDateTime.now().plusDays(1),
            limitedQuantity = 50,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.OPEN,
        )
        val product = sampleSneakerProduct()

        every { limitedDropRepository.findAllActive() } returns listOf(firstDrop, secondDrop)
        every { dropReservationStore.remaining(any()) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { dropReservationStore.scanStaleReservations(any(), any()) } returns emptyList()

        When("reconcileAllActive를 호출하면") {
            service.reconcileAllActive()

            Then("활성 회차 각각에 대해 대사를 수행한다") {
                verify(exactly = 2) { dropReservationStore.remaining(any()) }
                verify(exactly = 1) { goodsDomainService.getProductWithStock(PRODUCT_ID) }
                verify(exactly = 1) { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) }
            }
        }
    }

    Given("활성 회차가 존재하지 않는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)

        every { limitedDropRepository.findAllActive() } returns emptyList()

        When("reconcileAllActive를 호출하면") {
            service.reconcileAllActive()

            Then("Redis·상품 조회를 전혀 수행하지 않고 조기 반환한다") {
                verify(exactly = 0) { dropReservationStore.remaining(any()) }
                verify(exactly = 0) { goodsDomainService.getProductWithStock(any()) }
            }
        }
    }

    Given("활성 회차 2건 중 첫 번째 회차의 Redis 조회가 장애 상태인 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val firstDrop = openDrop()
        val secondDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID + 1,
            openAt = ZonedDateTime.now().minusMinutes(1),
            closeAt = ZonedDateTime.now().plusDays(1),
            limitedQuantity = 50,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.OPEN,
        )
        val product = sampleSneakerProduct()

        every { limitedDropRepository.findAllActive() } returns listOf(firstDrop, secondDrop)
        every { dropReservationStore.remaining(DROP_ID) } throws
            DataAccessResourceFailureException("redis down") andThen 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) } returns
            ProductWithStock(product = product, stockQuantity = 50)
        every { dropReservationStore.scanStaleReservations(any(), any()) } returns emptyList()

        When("reconcileAllActive를 호출하면") {
            Then("예외를 전파하지 않고 나머지 회차 대사를 계속 수행한다") {
                shouldNotThrowAny { service.reconcileAllActive() }
                verify(exactly = 2) { dropReservationStore.remaining(DROP_ID) }
                verify(exactly = 0) { goodsDomainService.getProductWithStock(PRODUCT_ID) }
                verify(exactly = 1) { goodsDomainService.getProductWithStock(PRODUCT_ID + 1) }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // FIX-04: 리컨실리에이션 언더셀(예약-주문 불일치) 감지·복원
    // ---------------------------------------------------------------------------------------

    Given("[FIX-04] 예약 마커는 있으나 대응 주문이 없고 유예 시간이 지난 고립 예약이 있는 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val drop = openDrop()
        val product = sampleSneakerProduct()
        val orphan = PendingReservation(idempotencyKey = "idem-orphan-1", userId = USER_ID, quantity = QUANTITY)
        val eventsSlot = slot<List<DomainEvent>>()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { dropReservationStore.scanStaleReservations(DROP_ID, 60L) } returns listOf(orphan)
        every { goodsDomainService.hasOrderFor("idem-orphan-1") } returns false
        every {
            dropReservationStore.restoreOrphanedReservation(DROP_ID, USER_ID, QUANTITY, "idem-orphan-1")
        } returns true
        every { domainEventPublisher.publishAll(capture(eventsSlot)) } returns Unit

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("고립 예약을 cancel 경로(restoreOrphanedReservation)로 복원한다") {
                verify(exactly = 1) {
                    dropReservationStore.restoreOrphanedReservation(DROP_ID, USER_ID, QUANTITY, "idem-orphan-1")
                }
            }

            Then("복원 건수·후보 수를 담은 LimitedDropUnderRestoredEvent를 발행한다") {
                eventsSlot.captured shouldHaveSize 1
                val event = eventsSlot.captured[0] as LimitedDropUnderRestoredEvent
                event.dropId shouldBe DROP_ID
                event.restoredCount shouldBe 1
                event.staleReservationCount shouldBe 1
            }
        }
    }

    Given("[FIX-04] 유예 시간이 지나지 않은 예약만 있는 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val graceSeconds = 90L
        val service = buildService(
            limitedDropRepository,
            dropReservationStore,
            goodsDomainService,
            domainEventPublisher,
            underSellGraceSeconds = graceSeconds,
        )
        val drop = openDrop()
        val product = sampleSneakerProduct()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        // 유예 시간 이내 예약 제외는 게이트웨이(SCAN+TTL)의 책임 — 도메인은 설정된 graceSeconds를
        // 그대로 게이트웨이에 전달했는지만 검증한다(협업 경계). 실제 TTL 필터링 검증은
        // DropReservationStoreImplTest(Testcontainers)에서 수행한다.
        every { dropReservationStore.scanStaleReservations(DROP_ID, graceSeconds) } returns emptyList()

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("설정된 유예 시간(graceSeconds)을 그대로 게이트웨이에 전달하고, 복원 대상이 없으므로 복원을 수행하지 않는다") {
                verify(exactly = 1) { dropReservationStore.scanStaleReservations(DROP_ID, graceSeconds) }
                verify(exactly = 0) { dropReservationStore.restoreOrphanedReservation(any(), any(), any(), any()) }
            }
        }
    }

    Given("[FIX-04] 예약 마커는 있으나 대응 주문이 이미 존재하는 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val drop = openDrop()
        val product = sampleSneakerProduct()
        val settled = PendingReservation(idempotencyKey = "idem-settled-1", userId = USER_ID, quantity = QUANTITY)

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { dropReservationStore.scanStaleReservations(DROP_ID, 60L) } returns listOf(settled)
        every { goodsDomainService.hasOrderFor("idem-settled-1") } returns true

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("주문이 존재하는 예약은 복원 대상에서 제외해 restoreOrphanedReservation을 호출하지 않는다") {
                verify(exactly = 0) { dropReservationStore.restoreOrphanedReservation(any(), any(), any(), any()) }
                verify(exactly = 0) { domainEventPublisher.publishAll(any()) }
            }
        }
    }

    Given("[FIX-04] 같은 고립 예약을 두 번의 대사 주기에 걸쳐 처리하는 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService, domainEventPublisher)
        val drop = openDrop()
        val product = sampleSneakerProduct()
        val orphan = PendingReservation(idempotencyKey = "idem-orphan-2", userId = USER_ID, quantity = QUANTITY)

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { goodsDomainService.hasOrderFor("idem-orphan-2") } returns false
        // 첫 주기: 마커가 남아있어 SCAN에 잡힘 → cancel.lua가 실제로 복원(Restored)
        every { dropReservationStore.scanStaleReservations(DROP_ID, 60L) } returnsMany listOf(listOf(orphan), emptyList())
        every {
            dropReservationStore.restoreOrphanedReservation(DROP_ID, USER_ID, QUANTITY, "idem-orphan-2")
        } returns true

        When("reconcile를 연속 두 번 호출하면") {
            service.reconcile(DROP_ID)
            service.reconcile(DROP_ID)

            Then("[cancel.lua 멱등] 복원 후 마커가 사라져 두 번째 주기의 SCAN에는 더 이상 잡히지 않고, restoreOrphanedReservation은 정확히 1회만 호출된다") {
                verify(exactly = 1) {
                    dropReservationStore.restoreOrphanedReservation(DROP_ID, USER_ID, QUANTITY, "idem-orphan-2")
                }
            }
        }
    }

    Given("[FIX-04] 언더셀 대사 도중 Redis 조회(scanStaleReservations)가 장애 상태인 상황") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val firstDrop = openDrop()
        val secondDrop = LimitedDrop.reconstitute(
            productId = PRODUCT_ID + 1,
            openAt = ZonedDateTime.now().minusMinutes(1),
            closeAt = ZonedDateTime.now().plusDays(1),
            limitedQuantity = 50,
            perUserLimit = PER_USER_LIMIT,
            status = LimitedDropStatus.OPEN,
        )
        val product = sampleSneakerProduct()

        every { limitedDropRepository.findAllActive() } returns listOf(firstDrop, secondDrop)
        every { dropReservationStore.remaining(any()) } returns 30
        every { goodsDomainService.getProductWithStock(any()) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { dropReservationStore.scanStaleReservations(DROP_ID, 60L) } throws
            DataAccessResourceFailureException("redis down") andThen emptyList()

        When("reconcileAllActive를 호출하면") {
            Then("예외를 전파하지 않고(fail-open) 다음 회차·다음 주기 대사를 계속 수행한다") {
                shouldNotThrowAny { service.reconcileAllActive() }
                verify(exactly = 2) { dropReservationStore.scanStaleReservations(DROP_ID, 60L) }
            }
        }
    }

    Given("[FIX-04] 복원 대상이 0건인 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(limitedDropRepository, dropReservationStore, goodsDomainService)
        val drop = openDrop()
        val product = sampleSneakerProduct()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)
        every { dropReservationStore.scanStaleReservations(DROP_ID, 60L) } returns emptyList()

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("Redis 쓰기 명령(restoreOrphanedReservation)을 전혀 발행하지 않는다") {
                verify(exactly = 0) { dropReservationStore.restoreOrphanedReservation(any(), any(), any(), any()) }
            }
        }
    }

    Given("[FIX-04] 언더셀 복원 플래그가 OFF인 회차") {
        val limitedDropRepository = mockk<LimitedDropRepository>()
        val dropReservationStore = mockk<DropReservationStore>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val service = buildService(
            limitedDropRepository,
            dropReservationStore,
            goodsDomainService,
            underSellReconciliationEnabled = false,
        )
        val drop = openDrop()
        val product = sampleSneakerProduct()

        every { limitedDropRepository.findById(DROP_ID) } returns drop
        every { dropReservationStore.remaining(DROP_ID) } returns 30
        every { goodsDomainService.getProductWithStock(PRODUCT_ID) } returns ProductWithStock(product = product, stockQuantity = 50)

        When("reconcile를 호출하면") {
            service.reconcile(DROP_ID)

            Then("언더셀 스캔 자체를 호출하지 않아 롤백(플래그 OFF)이 즉시 적용된다") {
                verify(exactly = 0) { dropReservationStore.scanStaleReservations(any(), any()) }
                verify(exactly = 0) { dropReservationStore.restoreOrphanedReservation(any(), any(), any(), any()) }
            }

            Then("기존 오버셀 판정은 플래그와 무관하게 그대로 동작한다") {
                verify(exactly = 1) { dropReservationStore.remaining(DROP_ID) }
                verify(exactly = 1) { goodsDomainService.getProductWithStock(PRODUCT_ID) }
            }
        }
    }
})

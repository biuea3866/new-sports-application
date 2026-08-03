package com.sportsapp.domain.booking

import com.sportsapp.domain.booking.dto.BookingTitleLabel
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureFlagEvaluator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

/**
 * 예약 목록은 사용자에게 "무슨 예약인지"를 보여줘야 하는데, 목록 조회가 Slot을 조인하지 않아
 * title이 항상 null이었고 화면이 예약 PK(`예약 #2`)를 대신 노출했다.
 * 목록도 단건 상세와 동일하게 Slot을 조인해 라벨을 채우는지 검증한다.
 */
class BookingDomainServiceFindMyBookingDetailsTest : BehaviorSpec({

    val bookingRepository = mockk<BookingRepository>()
    val slotRepository = mockk<SlotRepository>()
    val distributedLock = mockk<DistributedLock>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()
    val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()

    val service = BookingDomainService(
        bookingRepository,
        slotRepository,
        distributedLock,
        domainEventPublisher,
        bookingOrderQueryRepository,
        featureFlagEvaluator,
    )

    fun bookingMock(bookingId: Long, slotId: Long): Booking = mockk<Booking>(relaxed = true) {
        every { id } returns bookingId
        every { userId } returns 1L
        every { this@mockk.slotId } returns slotId
        every { status } returns BookingStatus.CONFIRMED
        every { paymentId } returns null
        every { createdAt } returns ZonedDateTime.now()
        every { updatedAt } returns ZonedDateTime.now()
    }

    fun slotMock(slotId: Long, timeRange: String): Slot = mockk<Slot>(relaxed = true) {
        every { id } returns slotId
        every { facilityId } returns "fac-001"
        every { date } returns ZonedDateTime.of(2026, 8, 1, 0, 0, 0, 0, ZoneOffset.UTC)
        every { this@mockk.timeRange } returns timeRange
    }

    Given("Slot이 있는 예약 2건") {
        val pageable = PageRequest.of(0, 20)
        val bookings = listOf(bookingMock(1L, 10L), bookingMock(2L, 11L))
        every {
            bookingRepository.findPageByUserId(1L, null, pageable)
        } returns PageImpl(bookings, pageable, 2L)
        every {
            slotRepository.findAllByIds(listOf(10L, 11L))
        } returns listOf(slotMock(10L, "07:00-08:00"), slotMock(11L, "10:00-11:00"))

        When("목록 상세를 조회하면") {
            val page = service.findMyBookingDetails(userId = 1L, status = null, pageable = pageable)

            Then("각 예약에 Slot 기반 라벨이 채워진다") {
                page.content[0].title shouldBe "2026-08-01 07:00-08:00 시설 예약"
                page.content[1].title shouldBe "2026-08-01 10:00-11:00 시설 예약"
            }

            Then("시설 식별자도 함께 채워진다") {
                page.content[0].facilityId shouldBe "fac-001"
            }

            Then("Slot을 예약 건수만큼 개별 조회하지 않고 한 번에 조회한다") {
                page.totalElements shouldBe 2L
            }
        }
    }

    Given("Slot이 삭제돼 조인되지 않는 예약") {
        val pageable = PageRequest.of(0, 20)
        every {
            bookingRepository.findPageByUserId(2L, null, pageable)
        } returns PageImpl(listOf(bookingMock(3L, 99L)), pageable, 1L)
        every { slotRepository.findAllByIds(listOf(99L)) } returns emptyList()

        When("목록 상세를 조회하면") {
            val page = service.findMyBookingDetails(userId = 2L, status = null, pageable = pageable)

            Then("기본 라벨로 방어 반환한다") {
                page.content[0].title shouldBe BookingTitleLabel.DEFAULT_TITLE
                page.content[0].facilityId shouldBe null
            }
        }
    }

    Given("예약이 없는 사용자") {
        val pageable = PageRequest.of(0, 20)
        every {
            bookingRepository.findPageByUserId(3L, null, pageable)
        } returns PageImpl(emptyList(), pageable, 0L)
        every { slotRepository.findAllByIds(emptyList()) } returns emptyList()

        When("목록 상세를 조회하면") {
            val page = service.findMyBookingDetails(userId = 3L, status = null, pageable = pageable)

            Then("빈 페이지를 반환한다") {
                page.totalElements shouldBe 0L
            }
        }
    }
})

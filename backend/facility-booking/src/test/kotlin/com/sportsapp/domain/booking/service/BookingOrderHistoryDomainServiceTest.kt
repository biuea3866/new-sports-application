package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class BookingOrderHistoryDomainServiceTest : BehaviorSpec({

    val bookingRepository = mockk<BookingRepository>()
    val slotRepository = mockk<SlotRepository>()
    val distributedLock = mockk<DistributedLock>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()

    val service = BookingDomainService(
        bookingRepository,
        slotRepository,
        distributedLock,
        domainEventPublisher,
        bookingOrderQueryRepository,
    )

    Given("사용자 7의 예약이 라벨과 함께 존재하는 상황") {
        val item = BookingOrderItem.of(
            bookingId = 1L,
            slotId = 42L,
            userId = 7L,
            status = BookingStatus.CONFIRMED,
            paymentId = 100L,
            createdAt = ZonedDateTime.now(),
            slotDate = ZonedDateTime.now(),
            slotTimeRange = "09:00-10:00",
        )
        every { bookingOrderQueryRepository.findByUserId(7L) } returns listOf(item)

        When("findOrderHistory를 호출하면") {
            val result = service.findOrderHistory(7L)

            Then("라벨이 포함된 사용자별 주문 목록이 반환된다") {
                result shouldHaveSize 1
                result[0].title shouldBe item.title
                result[0].userId shouldBe 7L
            }
        }
    }

    Given("사용자 8의 예약이 없는 상황") {
        every { bookingOrderQueryRepository.findByUserId(8L) } returns emptyList()

        When("findOrderHistory를 호출하면") {
            val result = service.findOrderHistory(8L)

            Then("빈 목록이 반환된다") {
                result.shouldBeEmpty()
            }
        }
    }
})

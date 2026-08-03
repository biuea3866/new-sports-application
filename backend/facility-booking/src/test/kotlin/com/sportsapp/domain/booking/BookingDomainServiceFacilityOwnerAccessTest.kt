package com.sportsapp.domain.booking

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureFlagEvaluator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * 예약 단건 접근 권한 — 예약자 본인 외에 **그 슬롯을 소유한 시설 파트너**도 열람할 수 있어야 한다.
 *
 * 포털 "예약 관리"가 파트너 스코프 목록을 보여주게 되면서, 목록의 행을 눌러 상세를 열 수 있어야
 * 기능이 성립한다. 예약자만 허용하면 파트너는 자기 시설 예약 목록을 보고도 상세에서 막힌다.
 * 소유권 판정은 슬롯(`slots.owner_id`)으로 한다 — 슬롯은 booking 컨텍스트 자기 애그리게이트라
 * 도메인 교차가 아니다.
 */
class BookingDomainServiceFacilityOwnerAccessTest : BehaviorSpec({

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

    val bookerUserId = 68L
    val facilityOwnerUserId = 69L
    val strangerUserId = 999L
    val targetBookingId = 10L
    val targetSlotId = 42L

    fun stubBooking() = mockk<Booking>(relaxed = true) {
        every { id } returns targetBookingId
        every { slotId } returns targetSlotId
        every { userId } returns bookerUserId
    }

    fun stubSlot(slotOwnerId: Long) = mockk<Slot>(relaxed = true) {
        every { id } returns targetSlotId
        every { ownerId } returns slotOwnerId
    }

    Given("내 시설 슬롯에 다른 사람이 건 예약") {
        val booking = stubBooking()
        every { bookingRepository.findById(targetBookingId) } returns booking
        every { slotRepository.findById(targetSlotId) } returns stubSlot(slotOwnerId = facilityOwnerUserId)

        When("시설 소유자가 예약을 조회하면") {
            val result = service.getBooking(facilityOwnerUserId, targetBookingId)

            Then("예약자가 아니어도 열람할 수 있다") {
                result.id shouldBe targetBookingId
            }
        }
    }

    Given("예약자 본인인 경우") {
        val booking = stubBooking()
        every { bookingRepository.findById(targetBookingId) } returns booking
        every { slotRepository.findById(targetSlotId) } returns stubSlot(slotOwnerId = facilityOwnerUserId)

        When("예약자가 예약을 조회하면") {
            val result = service.getBooking(bookerUserId, targetBookingId)

            Then("기존대로 열람할 수 있다") {
                result.id shouldBe targetBookingId
            }
        }
    }

    Given("예약자도 시설 소유자도 아닌 제3자") {
        val booking = stubBooking()
        every { bookingRepository.findById(targetBookingId) } returns booking
        every { slotRepository.findById(targetSlotId) } returns stubSlot(slotOwnerId = facilityOwnerUserId)

        When("제3자가 예약을 조회하면") {
            Then("권한 예외가 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    service.getBooking(strangerUserId, targetBookingId)
                }
            }
        }
    }

    Given("다른 파트너 시설의 예약") {
        val booking = stubBooking()
        every { bookingRepository.findById(targetBookingId) } returns booking
        every { slotRepository.findById(targetSlotId) } returns stubSlot(slotOwnerId = 70L)

        When("내가 그 슬롯의 소유자가 아닌 채로 조회하면") {
            // 권한 누수 방지 — 남의 시설 예약은 파트너라도 열람 불가.
            Then("권한 예외가 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    service.getBooking(facilityOwnerUserId, targetBookingId)
                }
            }
        }
    }

    Given("참조 슬롯이 사라진 예약") {
        val booking = stubBooking()
        every { bookingRepository.findById(targetBookingId) } returns booking
        every { slotRepository.findById(targetSlotId) } returns null

        When("예약자가 아닌 사용자가 조회하면") {
            // 슬롯이 없으면 소유권을 확인할 방법이 없다 — 열어주지 않는다.
            Then("권한 예외가 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    service.getBooking(facilityOwnerUserId, targetBookingId)
                }
            }
        }
    }
})

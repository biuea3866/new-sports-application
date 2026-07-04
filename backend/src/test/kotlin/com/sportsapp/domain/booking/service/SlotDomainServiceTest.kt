package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.exception.SlotFacilityNotFoundException
import com.sportsapp.domain.booking.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.booking.gateway.FacilityOwnershipGateway
import com.sportsapp.domain.booking.repository.SlotRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class SlotDomainServiceTest : BehaviorSpec({

    val slotRepository = mockk<SlotRepository>()
    val facilityOwnershipGateway = mockk<FacilityOwnershipGateway>()
    val service = SlotDomainService(slotRepository, facilityOwnershipGateway)

    every { slotRepository.save(any()) } answers { firstArg() }

    Given("요청자가 시설의 소유자인 경우") {
        every { facilityOwnershipGateway.requireOwner("FAC-01", 1L) } just Runs

        When("슬롯을 생성하면") {
            val slot = service.createSlot(
                ownerId = 1L,
                facilityId = "FAC-01",
                date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
                timeRange = "09:00-10:00",
                capacity = 5,
            )

            Then("시설 소유권을 검증하고 슬롯을 저장한다") {
                slot.facilityId shouldBe "FAC-01"
                slot.ownerId shouldBe 1L
                verify(exactly = 1) { facilityOwnershipGateway.requireOwner("FAC-01", 1L) }
                verify(exactly = 1) { slotRepository.save(any<Slot>()) }
            }
        }
    }

    Given("존재하지 않는 시설로 슬롯을 생성하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        every {
            localGateway.requireOwner("FAC-404", 1L)
        } throws SlotFacilityNotFoundException("FAC-404")

        When("슬롯을 생성하면") {
            Then("SlotFacilityNotFoundException을 던지고 슬롯을 저장하지 않는다") {
                shouldThrow<SlotFacilityNotFoundException> {
                    localService.createSlot(
                        ownerId = 1L,
                        facilityId = "FAC-404",
                        date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
                        timeRange = "09:00-10:00",
                        capacity = 5,
                    )
                }
                verify(exactly = 0) { localSlotRepository.save(any<Slot>()) }
            }
        }
    }

    Given("시설 소유자가 아닌 사용자가 슬롯을 생성하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        every {
            localGateway.requireOwner("FAC-02", 9L)
        } throws UnauthorizedFacilityAccessException("FAC-02", 9L)

        When("슬롯을 생성하면") {
            Then("UnauthorizedFacilityAccessException을 던지고 슬롯을 저장하지 않는다") {
                shouldThrow<UnauthorizedFacilityAccessException> {
                    localService.createSlot(
                        ownerId = 9L,
                        facilityId = "FAC-02",
                        date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
                        timeRange = "09:00-10:00",
                        capacity = 5,
                    )
                }
                verify(exactly = 0) { localSlotRepository.save(any<Slot>()) }
            }
        }
    }
})

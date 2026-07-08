package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.entity.SlotStatus
import com.sportsapp.domain.booking.exception.SlotFacilityNotFoundException
import com.sportsapp.domain.booking.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.booking.gateway.FacilityOwnershipGateway
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
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

    Given("존재하는 슬롯의 소유자가 close를 요청하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        val slot = Slot.create(
            facilityId = "FAC-CLOSE-01",
            date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        every { localSlotRepository.findById(1L) } returns slot
        every { localSlotRepository.save(any()) } answers { firstArg() }

        When("closeSlot을 호출하면") {
            val result = localService.closeSlot(requesterId = 1L, slotId = 1L)

            Then("슬롯 status가 CLOSED로 저장된다") {
                result.status shouldBe SlotStatus.CLOSED
                verify(exactly = 1) { localSlotRepository.save(slot) }
            }
        }
    }

    Given("존재하지 않는 슬롯의 close를 요청하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        every { localSlotRepository.findById(404L) } returns null

        When("closeSlot을 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    localService.closeSlot(requesterId = 1L, slotId = 404L)
                }
            }
        }
    }

    Given("CLOSED 슬롯의 소유자가 open을 요청하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        val slot = Slot.create(
            facilityId = "FAC-OPEN-01",
            date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
            timeRange = "09:00-10:00",
            capacity = 5,
            ownerId = 1L,
        )
        slot.close(1L)
        every { localSlotRepository.findById(2L) } returns slot
        every { localSlotRepository.save(any()) } answers { firstArg() }

        When("openSlot을 호출하면") {
            val result = localService.openSlot(requesterId = 1L, slotId = 2L)

            Then("슬롯 status가 OPEN으로 저장된다") {
                result.status shouldBe SlotStatus.OPEN
            }
        }
    }

    Given("programId 필터로 슬롯 목록을 조회하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        val filtered = listOf(
            Slot.create(
                facilityId = "FAC-01",
                date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
                timeRange = "09:00-10:00",
                capacity = 5,
                ownerId = 1L,
                programId = 10L,
            ),
        )
        every { localSlotRepository.findByFacilityId("FAC-01", 10L) } returns filtered

        When("listSlots(facilityId, programId=10)을 호출하면") {
            val result = localService.listSlots("FAC-01", 10L)

            Then("programId=10인 슬롯만 반환된다") {
                result shouldBe filtered
                verify(exactly = 1) { localSlotRepository.findByFacilityId("FAC-01", 10L) }
            }
        }
    }

    Given("programId 필터 없이 슬롯 목록을 조회하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        val all = listOf(
            Slot.create(
                facilityId = "FAC-01",
                date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
                timeRange = "09:00-10:00",
                capacity = 5,
                ownerId = 1L,
            ),
            Slot.create(
                facilityId = "FAC-01",
                date = ZonedDateTime.parse("2026-07-01T10:00:00+09:00"),
                timeRange = "10:00-11:00",
                capacity = 5,
                ownerId = 1L,
                programId = 10L,
            ),
        )
        every { localSlotRepository.findByFacilityId("FAC-01", null) } returns all

        When("listSlots(facilityId, programId=null)을 호출하면") {
            val result = localService.listSlots("FAC-01", null)

            Then("시설의 전체 슬롯이 반환된다") {
                result shouldBe all
            }
        }
    }

    Given("시설 소유자가 programId를 지정해 슬롯을 생성하는 경우") {
        val localSlotRepository = mockk<SlotRepository>()
        val localGateway = mockk<FacilityOwnershipGateway>()
        val localService = SlotDomainService(localSlotRepository, localGateway)
        every { localGateway.requireOwner("FAC-PROGRAM-01", 1L) } just Runs
        every { localSlotRepository.save(any()) } answers { firstArg() }

        When("createSlot에 programId를 전달하면") {
            val slot = localService.createSlot(
                ownerId = 1L,
                facilityId = "FAC-PROGRAM-01",
                date = ZonedDateTime.parse("2026-07-01T09:00:00+09:00"),
                timeRange = "09:00-10:00",
                capacity = 5,
                programId = 55L,
            )

            Then("생성된 슬롯이 programId를 보관한다") {
                slot.programId shouldBe 55L
            }
        }
    }
})

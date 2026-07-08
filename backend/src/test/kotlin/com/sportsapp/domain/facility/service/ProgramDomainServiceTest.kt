package com.sportsapp.domain.facility.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.facility.entity.Facility
import com.sportsapp.domain.facility.entity.Program
import com.sportsapp.domain.facility.exception.FacilityNotFoundException
import com.sportsapp.domain.facility.exception.UnauthorizedProgramAccessException
import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.repository.ProgramCustomRepository
import com.sportsapp.domain.facility.repository.ProgramRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.geo.Point

class ProgramDomainServiceTest : BehaviorSpec({

    val programRepository = mockk<ProgramRepository>()
    val facilityRepository = mockk<FacilityRepository>()
    val programCustomRepository = mockk<ProgramCustomRepository>()
    val programDomainService = ProgramDomainService(
        programRepository = programRepository,
        facilityRepository = facilityRepository,
        programCustomRepository = programCustomRepository,
    )

    fun facility(id: String = "FAC-01", ownerUserId: Long? = 1L) = Facility(
        id = id,
        code = "CODE-$id",
        name = "시설 $id",
        gu = "강남구",
        type = "체육관",
        address = "서울시 강남구",
        location = Point(127.0, 37.5),
        parking = true,
        tel = "02-0000-0000",
        homePage = "",
        eduYn = false,
        meta = emptyMap(),
        ownerUserId = ownerUserId,
        sidoCode = null,
        sidoName = null,
        sigunguCode = null,
        sigunguName = null,
    )

    Given("소유자가 PT 상품(정원1·60분)을 등록하는 경우") {
        every { facilityRepository.findById("FAC-01") } returns facility(ownerUserId = 1L)
        every { programRepository.save(any()) } answers { firstArg() }

        When("register 를 호출하면") {
            val program = programDomainService.register(
                facilityId = "FAC-01",
                ownerUserId = 1L,
                name = "1:1 PT",
                description = "개인 트레이닝",
                price = BigDecimal("50000"),
                capacity = 1,
                durationMinutes = 60,
            )

            Then("Program 이 저장된다") {
                program.facilityId shouldBe "FAC-01"
                program.ownerUserId shouldBe 1L
                program.capacity shouldBe 1
                verify { programRepository.save(any()) }
            }
        }
    }

    Given("존재하지 않는 시설에 상품을 등록하는 경우") {
        every { facilityRepository.findById("FAC-NONE") } returns null

        When("register 를 호출하면") {
            Then("FacilityNotFoundException 이 발생한다") {
                shouldThrow<FacilityNotFoundException> {
                    programDomainService.register(
                        facilityId = "FAC-NONE",
                        ownerUserId = 1L,
                        name = "1:1 PT",
                        description = null,
                        price = BigDecimal.ZERO,
                        capacity = 1,
                        durationMinutes = 60,
                    )
                }
            }
        }
    }

    Given("소유자가 아닌 사용자가 상품을 등록하는 경우") {
        every { facilityRepository.findById("FAC-01") } returns facility(ownerUserId = 1L)

        When("register 를 호출하면") {
            Then("UnauthorizedFacilityAccessException 이 발생한다") {
                shouldThrow<com.sportsapp.domain.facility.exception.UnauthorizedFacilityAccessException> {
                    programDomainService.register(
                        facilityId = "FAC-01",
                        ownerUserId = 99L,
                        name = "1:1 PT",
                        description = null,
                        price = BigDecimal.ZERO,
                        capacity = 1,
                        durationMinutes = 60,
                    )
                }
            }
        }
    }

    Given("시설상품 목록 조회") {
        val program = Program.create(
            facilityId = "FAC-LIST-01",
            ownerUserId = 1L,
            name = "그룹 클래스",
            description = null,
            price = BigDecimal.ZERO,
            capacity = 10,
            durationMinutes = 50,
        )
        every { programRepository.findByFacilityId("FAC-LIST-01") } returns listOf(program)

        When("findByFacility 를 호출하면") {
            val result = programDomainService.findByFacility("FAC-LIST-01")

            Then("등록된 program 을 반환한다") {
                result shouldHaveSize 1
                result[0].name shouldBe "그룹 클래스"
            }
        }
    }

    Given("소유한 program 을 조회하는 경우") {
        val program = Program.create(
            facilityId = "FAC-01",
            ownerUserId = 1L,
            name = "1:1 PT",
            description = null,
            price = BigDecimal.ZERO,
            capacity = 1,
            durationMinutes = 60,
        )
        every { programRepository.findById(10L) } returns program

        When("본인이 getOwnedProgram 을 호출하면") {
            val result = programDomainService.getOwnedProgram(1L, 10L)

            Then("program 을 반환한다") {
                result.name shouldBe "1:1 PT"
            }
        }

        When("타인이 getOwnedProgram 을 호출하면") {
            Then("UnauthorizedProgramAccessException 이 발생한다") {
                shouldThrow<UnauthorizedProgramAccessException> {
                    programDomainService.getOwnedProgram(99L, 10L)
                }
            }
        }
    }

    Given("존재하지 않는 program 을 조회하는 경우") {
        every { programRepository.findById(999L) } returns null

        When("getOwnedProgram 을 호출하면") {
            Then("ResourceNotFoundException 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    programDomainService.getOwnedProgram(1L, 999L)
                }
            }
        }
    }

    Given("catalog 통합 검색을 요청하는 경우") {
        val program = Program.create(
            facilityId = "FAC-CATALOG-01",
            ownerUserId = 1L,
            name = "요가 클래스",
            description = null,
            price = BigDecimal("30000"),
            capacity = 5,
            durationMinutes = 50,
        )
        val pageable = PageRequest.of(0, 20)
        val page: Page<Program> = PageImpl(listOf(program), pageable, 1)
        every { programCustomRepository.searchForCatalog("요가", pageable) } returns page

        When("searchForCatalog 를 호출하면") {
            val result = programDomainService.searchForCatalog("요가", pageable)

            Then("ProgramCustomRepository 조회 결과를 그대로 반환한다") {
                result.totalElements shouldBe 1
                result.content[0].name shouldBe "요가 클래스"
                verify { programCustomRepository.searchForCatalog("요가", pageable) }
            }
        }
    }
})

package com.sportsapp.application.user

import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.facility.FacilityDomainService
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.user.User
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.domain.user.UserStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetOperatorProfileUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val facilityDomainService = mockk<FacilityDomainService>()
    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = GetOperatorProfileUseCase(userDomainService, facilityDomainService, goodsDomainService)

    Given("[U-05] 자기 프로필 조회") {
        val user = mockk<User>(relaxed = true) {
            every { id } returns 1L
            every { email } returns "operator@test.com"
            every { status } returns UserStatus.ACTIVE
        }
        every { userDomainService.findByIdWithRoles(1L) } returns user
        every { facilityDomainService.countByOwnerUserId(1L) } returns 3L
        every { goodsDomainService.countActiveProductsByOwnerId(1L) } returns 5L
        every { userDomainService.countActiveTokensByUserId(1L) } returns 1L

        When("[U-05] 자기 userId로 execute 호출 시") {
            val command = GetOperatorProfileCommand(requestUserId = 1L, targetUserId = 1L)
            val response = useCase.execute(command)

            Then("[U-05] 자기 userId와 같은 userId를 요청하면 프로필이 정상 반환된다") {
                response.userId shouldBe 1L
                response.email shouldBe "operator@test.com"
                response.facilityCount shouldBe 3L
                response.activeProductCount shouldBe 5L
                response.activeTokenCount shouldBe 1L
            }
        }
    }

    Given("[U-05b] 다른 userId 프로필 조회 시도") {
        When("[U-05b] 다른 userId로 execute 호출 시") {
            val command = GetOperatorProfileCommand(requestUserId = 1L, targetUserId = 2L)

            Then("[U-05] 자기 userId와 다른 userId를 요청하면 예외가 발생한다") {
                shouldThrow<UnauthorizedException> {
                    useCase.execute(command)
                }
            }
        }
    }
})

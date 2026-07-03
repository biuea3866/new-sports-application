package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.CreatePartnerCommand
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerApiKey
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.service.IssuedApiKey
import com.sportsapp.domain.partner.service.PartnerDomainService
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.entity.UserStatus
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify

class CreatePartnerUseCaseTest : BehaviorSpec({

    val userDomainService = mockk<UserDomainService>()
    val partnerDomainService = mockk<PartnerDomainService>()
    val ownershipGuard = mockk<OwnershipGuard>()
    val useCase = CreatePartnerUseCase(userDomainService, partnerDomainService, ownershipGuard)

    Given("ADMIN이 신규 Partner 등록을 요청할 때") {
        val adminId = 1L
        val command = CreatePartnerCommand(name = "스포츠몰")
        val syntheticUser = User.create("partner+integration@integration.local", "hashed-password")
        val partner = Partner.reconstitute(
            id = 10L,
            name = command.name,
            status = PartnerStatus.ACTIVE,
            linkedUserId = syntheticUser.id,
        )
        val issuedApiKey = IssuedApiKey(
            plainKey = "partner_1_random",
            apiKey = PartnerApiKey.reconstitute(
                id = 1L,
                partnerId = 10L,
                keyHash = "hashed-key",
                status = ApiKeyStatus.ACTIVE,
                revokedAt = null,
                lastUsedAt = null,
            ),
        )

        every { ownershipGuard.authUserId() } returns adminId
        every { userDomainService.register(any(), any()) } returns syntheticUser
        every { userDomainService.assignRole(adminId, syntheticUser.id, "GOODS_SELLER") } just Runs
        every { userDomainService.assignRole(adminId, syntheticUser.id, "EVENT_HOST") } just Runs
        every { partnerDomainService.createPartner(command.name, syntheticUser.id) } returns (partner to issuedApiKey)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("연동 User를 유니크한 synthetic 이메일과 랜덤 패스워드로 생성한다") {
                verify(exactly = 1) { userDomainService.register(any(), any()) }
            }

            Then("GOODS_SELLER와 EVENT_HOST 두 role을 부여한다") {
                verify(exactly = 1) { userDomainService.assignRole(adminId, syntheticUser.id, "GOODS_SELLER") }
                verify(exactly = 1) { userDomainService.assignRole(adminId, syntheticUser.id, "EVENT_HOST") }
            }

            Then("PartnerDomainService.createPartner를 연동 User id로 호출한다") {
                verify(exactly = 1) { partnerDomainService.createPartner(command.name, syntheticUser.id) }
            }

            Then("응답에 plainApiKey를 포함한다") {
                result.partnerId shouldBe 10L
                result.name shouldBe command.name
                result.status shouldBe PartnerStatus.ACTIVE
                result.plainApiKey shouldBe "partner_1_random"
            }
        }
    }

    Given("두 번째 role(EVENT_HOST) 부여가 실패하는 경우") {
        val adminId = 2L
        val command = CreatePartnerCommand(name = "실패케이스몰")
        val syntheticUser = User.create("partner+failure@integration.local", "hashed-password")

        every { ownershipGuard.authUserId() } returns adminId
        every { userDomainService.register(any(), any()) } returns syntheticUser
        every { userDomainService.assignRole(adminId, syntheticUser.id, "GOODS_SELLER") } just Runs
        every {
            userDomainService.assignRole(adminId, syntheticUser.id, "EVENT_HOST")
        } throws IllegalStateException("role assignment failed")

        When("execute를 호출하면") {
            Then("예외가 전파되고 PartnerDomainService.createPartner는 호출되지 않는다") {
                shouldThrow<IllegalStateException> { useCase.execute(command) }
                verify(exactly = 0) { partnerDomainService.createPartner(command.name, syntheticUser.id) }
            }
        }
    }
})

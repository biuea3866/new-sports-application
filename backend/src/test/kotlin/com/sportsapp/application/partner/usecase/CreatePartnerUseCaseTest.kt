package com.sportsapp.application.partner.usecase

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.sportsapp.application.partner.dto.CreatePartnerCommand
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerApiKey
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.service.IssuedApiKey
import com.sportsapp.domain.partner.service.PartnerDomainService
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.IntegrationAccountDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional

/**
 * PH0-08 T1 SAGA — CreatePartnerUseCase 오케스트레이션 + 보상 검증.
 * tx1(provision) -> tx2(createPartner) -> tx3(activate) 3단 로컬 트랜잭션을 UseCase 레벨에서
 * 트랜잭션 없이 오케스트레이션하고, 실패 시 보상(abandon / changeStatus)이 호출됨을 검증한다.
 *
 * Given 블록마다 Mock·UseCase를 새로 생성한다 — BehaviorSpec(SingleInstance)은 스펙 인스턴스를
 * 하나만 만들어 모든 Given 블록이 순차 실행되므로, mock을 루트에서 공유하면 이전 Given의
 * 호출 이력이 뒤 Given의 verify(exactly=0) 단언을 오염시킨다.
 */
class CreatePartnerUseCaseTest : BehaviorSpec({

    fun issuedApiKey(plainKey: String = "partner_1_random") = IssuedApiKey(
        plainKey = plainKey,
        apiKey = PartnerApiKey.reconstitute(
            id = 1L,
            partnerId = 10L,
            keyHash = "hashed-key",
            status = ApiKeyStatus.ACTIVE,
            revokedAt = null,
            lastUsedAt = null,
        ),
    )

    Given("ADMIN이 신규 Partner 등록을 요청하고 모든 단계가 성공할 때") {
        val integrationAccountDomainService = mockk<IntegrationAccountDomainService>()
        val partnerDomainService = mockk<PartnerDomainService>()
        val ownershipGuard = mockk<OwnershipGuard>()
        val useCase = CreatePartnerUseCase(integrationAccountDomainService, partnerDomainService, ownershipGuard)

        val adminId = 1L
        val command = CreatePartnerCommand(name = "스포츠몰")
        val provisionedUser = User.createInactive("partner+integration@integration.local", "hashed-password")
        val partner = Partner.reconstitute(
            id = 10L,
            name = command.name,
            status = PartnerStatus.ACTIVE,
            linkedUserId = provisionedUser.id,
        )
        val key = issuedApiKey()

        every { ownershipGuard.authUserId() } returns adminId
        every {
            integrationAccountDomainService.provision(adminId, listOf("GOODS_SELLER", "EVENT_HOST"))
        } returns provisionedUser
        every { partnerDomainService.createPartner(command.name, provisionedUser.id) } returns (partner to key)
        every { integrationAccountDomainService.activate(provisionedUser.id) } just Runs

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("연동 User를 GOODS_SELLER, EVENT_HOST 두 role과 함께 프로비저닝한다") {
                verify(exactly = 1) {
                    integrationAccountDomainService.provision(adminId, listOf("GOODS_SELLER", "EVENT_HOST"))
                }
            }

            Then("PartnerDomainService.createPartner를 연동 User id로 호출한다") {
                verify(exactly = 1) { partnerDomainService.createPartner(command.name, provisionedUser.id) }
            }

            Then("연동 User를 최종적으로 활성화한다") {
                verify(exactly = 1) { integrationAccountDomainService.activate(provisionedUser.id) }
            }

            Then("응답에 파트너 정보와 1회성 평문 API Key가 담긴다 (API 계약 보존)") {
                result.partnerId shouldBe 10L
                result.name shouldBe command.name
                result.status shouldBe PartnerStatus.ACTIVE
                result.plainApiKey shouldBe "partner_1_random"
            }
        }
    }

    Given("tx2(createPartner)가 실패하는 경우") {
        val integrationAccountDomainService = mockk<IntegrationAccountDomainService>()
        val partnerDomainService = mockk<PartnerDomainService>()
        val ownershipGuard = mockk<OwnershipGuard>()
        val useCase = CreatePartnerUseCase(integrationAccountDomainService, partnerDomainService, ownershipGuard)

        val adminId = 2L
        val command = CreatePartnerCommand(name = "실패케이스몰")
        val provisionedUser = User.createInactive("partner+failure@integration.local", "hashed-password")
        val failure = IllegalStateException("partner creation failed")

        every { ownershipGuard.authUserId() } returns adminId
        every {
            integrationAccountDomainService.provision(adminId, listOf("GOODS_SELLER", "EVENT_HOST"))
        } returns provisionedUser
        every { partnerDomainService.createPartner(command.name, provisionedUser.id) } throws failure
        every { integrationAccountDomainService.abandon(provisionedUser.id) } just Runs

        When("execute를 호출하면") {
            Then("원 예외가 전파되고 abandon 보상이 호출되며, activate는 호출되지 않는다") {
                val thrown = shouldThrow<IllegalStateException> { useCase.execute(command) }

                thrown shouldBe failure
                verify(exactly = 1) { integrationAccountDomainService.abandon(provisionedUser.id) }
                verify(exactly = 0) { integrationAccountDomainService.activate(any()) }
            }
        }
    }

    Given("tx3(activate)가 실패하는 경우") {
        val integrationAccountDomainService = mockk<IntegrationAccountDomainService>()
        val partnerDomainService = mockk<PartnerDomainService>()
        val ownershipGuard = mockk<OwnershipGuard>()
        val useCase = CreatePartnerUseCase(integrationAccountDomainService, partnerDomainService, ownershipGuard)

        val adminId = 3L
        val command = CreatePartnerCommand(name = "활성화실패몰")
        val provisionedUser = User.createInactive("partner+activate-fail@integration.local", "hashed-password")
        val partner = Partner.reconstitute(
            id = 20L,
            name = command.name,
            status = PartnerStatus.ACTIVE,
            linkedUserId = provisionedUser.id,
        )
        val key = issuedApiKey(plainKey = "partner_20_random")
        val failure = IllegalStateException("activate failed")

        every { ownershipGuard.authUserId() } returns adminId
        every {
            integrationAccountDomainService.provision(adminId, listOf("GOODS_SELLER", "EVENT_HOST"))
        } returns provisionedUser
        every { partnerDomainService.createPartner(command.name, provisionedUser.id) } returns (partner to key)
        every { integrationAccountDomainService.activate(provisionedUser.id) } throws failure
        every { partnerDomainService.changeStatus(20L, PartnerStatus.SUSPENDED) } just Runs

        When("execute를 호출하면") {
            Then("원 예외가 전파되고 파트너를 비활성화하는 보상이 호출된다") {
                val thrown = shouldThrow<IllegalStateException> { useCase.execute(command) }

                thrown shouldBe failure
                verify(exactly = 1) { partnerDomainService.changeStatus(20L, PartnerStatus.SUSPENDED) }
            }
        }
    }

    Given("tx2 실패 후 abandon 보상마저 실패하는 경우") {
        val integrationAccountDomainService = mockk<IntegrationAccountDomainService>()
        val partnerDomainService = mockk<PartnerDomainService>()
        val ownershipGuard = mockk<OwnershipGuard>()
        val useCase = CreatePartnerUseCase(integrationAccountDomainService, partnerDomainService, ownershipGuard)

        val adminId = 4L
        val command = CreatePartnerCommand(name = "보상실패몰")
        val provisionedUser = User.createInactive("partner+compensation-fail@integration.local", "hashed-password")
        val originalFailure = IllegalStateException("partner creation failed")
        val compensationFailure = IllegalStateException("abandon also failed")

        every { ownershipGuard.authUserId() } returns adminId
        every {
            integrationAccountDomainService.provision(adminId, listOf("GOODS_SELLER", "EVENT_HOST"))
        } returns provisionedUser
        every { partnerDomainService.createPartner(command.name, provisionedUser.id) } throws originalFailure
        every { integrationAccountDomainService.abandon(provisionedUser.id) } throws compensationFailure

        val listAppender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(CreatePartnerUseCase::class.java) as Logger
        logger.addAppender(listAppender)

        When("execute를 호출하면") {
            Then("원 예외(tx2 실패 원인)가 전파되고 보상 실패가 error 레벨 구조화 로그로 남는다") {
                val thrown = shouldThrow<IllegalStateException> { useCase.execute(command) }

                thrown shouldBe originalFailure
                val compensationLog = listAppender.list.find { it.level == Level.ERROR }
                compensationLog?.formattedMessage?.contains(provisionedUser.id.toString()) shouldBe true
            }
        }

        logger.detachAppender(listAppender)
    }

    Given("execute 메서드와 클래스에 @Transactional 클래스·메서드 트랜잭션 경계가 없는지 검사할 때") {
        When("리플렉션으로 애노테이션을 조회하면") {
            Then("CreatePartnerUseCase 클래스에는 @Transactional 이 없다") {
                CreatePartnerUseCase::class.java.isAnnotationPresent(Transactional::class.java) shouldBe false
            }

            Then("execute 메서드에는 @Transactional 이 없다") {
                val executeMethod = CreatePartnerUseCase::class.java.getDeclaredMethod(
                    "execute",
                    CreatePartnerCommand::class.java,
                )
                executeMethod.isAnnotationPresent(Transactional::class.java) shouldBe false
            }
        }
    }
})

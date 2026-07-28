package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.CreatePartnerCommand
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.domain.common.UserRoleName
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.service.IssuedApiKey
import com.sportsapp.domain.partner.service.PartnerDomainService
import com.sportsapp.domain.user.service.IntegrationAccountDomainService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * [PH0-08] T1 SAGA — B2B Partner 등록 오케스트레이션.
 *
 * user 컨텍스트와 partner 컨텍스트에 걸친 단일 트랜잭션 쓰기를 컨텍스트별 로컬 트랜잭션
 * 3단(tx1 provision -> tx2 createPartner -> tx3 activate)으로 분리한다. 이 클래스·메서드에는
 * 의도적으로 `@Transactional`을 두지 않는다 — 트랜잭션 경계는 각 DomainService 메서드가 갖는다
 * (결제 개시 4종 `CreateBookingUseCase` 등과 동일한 "UseCase 레벨 트랜잭션 없음" 패턴).
 *
 * tx2·tx3 실패 시 보상(abandon / changeStatus)으로 중간 상태를 무해화한다 — 상세 상태 전이표는
 * `도메인 경계 재설계/20260728-ph0-결합해소-거버넌스정합-tdd.md` 항목 5 참조.
 */
@Service
class CreatePartnerUseCase(
    private val integrationAccountDomainService: IntegrationAccountDomainService,
    private val partnerDomainService: PartnerDomainService,
    private val ownershipGuard: OwnershipGuard,
) {
    private val logger = LoggerFactory.getLogger(CreatePartnerUseCase::class.java)

    fun execute(command: CreatePartnerCommand): CreatePartnerResponse {
        val adminId = ownershipGuard.authUserId()
        val user = integrationAccountDomainService.provision(adminId, PARTNER_ROLE_NAMES)
        val (partner, issuedApiKey) = createPartnerOrCompensate(command.name, user.id)
        activateOrCompensate(user.id, partner)
        return CreatePartnerResponse.of(partner, issuedApiKey)
    }

    private fun createPartnerOrCompensate(name: String, userId: Long): Pair<Partner, IssuedApiKey> =
        runCatching { partnerDomainService.createPartner(name, userId) }
            .getOrElse { cause ->
                compensateAbandonUser(userId, cause)
                throw cause
            }

    private fun activateOrCompensate(userId: Long, partner: Partner) {
        runCatching { integrationAccountDomainService.activate(userId) }
            .onFailure { cause ->
                compensateDeactivatePartner(partner, cause)
                throw cause
            }
    }

    private fun compensateAbandonUser(userId: Long, cause: Throwable) {
        runCatching { integrationAccountDomainService.abandon(userId) }
            .onFailure { compensationFailure ->
                logCompensationFailure(step = "abandon-user", identifier = "userId=$userId", cause, compensationFailure)
            }
    }

    private fun compensateDeactivatePartner(partner: Partner, cause: Throwable) {
        val partnerId = requireNotNull(partner.id) { "Partner id must exist after save" }
        runCatching { partnerDomainService.changeStatus(partnerId, PartnerStatus.SUSPENDED) }
            .onFailure { compensationFailure ->
                logCompensationFailure(step = "deactivate-partner", identifier = "partnerId=$partnerId", cause, compensationFailure)
            }
    }

    private fun logCompensationFailure(step: String, identifier: String, cause: Throwable, compensationFailure: Throwable) {
        logger.error(
            "CreatePartner SAGA compensation failed: step={}, {}, cause={}, compensationCause={}",
            step,
            identifier,
            cause.message,
            compensationFailure.message,
            compensationFailure,
        )
    }

    private companion object {
        val PARTNER_ROLE_NAMES = listOf(UserRoleName.GOODS_SELLER.name, UserRoleName.EVENT_HOST.name)
    }
}

package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.ApplyRecruitmentCommand
import com.sportsapp.application.recruitment.dto.ApplyRecruitmentResult
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import java.util.UUID
import org.springframework.stereotype.Service

/**
 * 참가비 0원(무료) 모집은 PG 없이 즉시 CONFIRMED 확정한다.
 * 참가비>0 모집은 PENDING 신청 후 PaymentDomainService.createPending/initiatePg로 PG 결제를 개시한다
 * (OrderType.RECRUITMENT, BE-55). PG 웹훅 수신 후 OrderConfirmationGatewayImpl이
 * RecruitmentDomainService.confirmApplication을 호출해 최종 CONFIRMED로 전이한다.
 * PG 네트워크 호출을 감싸지 않도록 클래스 레벨 @Transactional을 두지 않는다(CreateBookingUseCase와 동형) —
 * recruitmentDomainService.apply/confirmApplication, paymentDomainService.createPending/initiatePg는
 * 각자 자기 트랜잭션 경계를 관리한다.
 */
@Service
class ApplyRecruitmentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    fun execute(command: ApplyRecruitmentCommand): ApplyRecruitmentResult {
        val recruitment = recruitmentDomainService.getRecruitment(command.recruitmentId)
        val applicationId = recruitmentDomainService.apply(command.recruitmentId, command.applicantUserId)
        return if (recruitment.isFree()) {
            confirmFree(applicationId)
        } else {
            initiatePayment(recruitment, applicationId, command)
        }
    }

    private fun confirmFree(applicationId: Long): ApplyRecruitmentResult {
        val application = recruitmentDomainService.confirmApplication(applicationId, null)
        return ApplyRecruitmentResult.of(application, paymentId = null, checkoutUrl = null)
    }

    private fun initiatePayment(
        recruitment: Recruitment,
        applicationId: Long,
        command: ApplyRecruitmentCommand,
    ): ApplyRecruitmentResult {
        val idempotencyKey = "recruitment:$applicationId:${UUID.randomUUID()}"
        val paymentId = paymentDomainService.createPending(
            userId = command.applicantUserId,
            idempotencyKey = idempotencyKey,
            orderType = OrderType.RECRUITMENT,
            orderId = applicationId,
            method = command.paymentMethod,
            amount = recruitment.feeAmount,
            currency = command.currency,
        )
        val pgResult = paymentDomainService.initiatePg(
            PgInitiateCommand(
                paymentId = paymentId,
                method = command.paymentMethod,
                idempotencyKey = idempotencyKey,
                userId = command.applicantUserId,
                orderType = OrderType.RECRUITMENT,
                orderId = applicationId,
                amount = recruitment.feeAmount,
                currency = command.currency,
                itemName = "RECRUITMENT #$applicationId",
                returnUrl = "",
                failUrl = "",
            ),
        )
        val application = recruitmentDomainService.getApplicationById(applicationId)
        return ApplyRecruitmentResult.of(application, paymentId = pgResult.paymentId, checkoutUrl = pgResult.checkoutUrl)
    }
}

package com.sportsapp.application.operator.usecase

import com.sportsapp.application.operator.dto.ListOperatorInboxNotificationsCommand
import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.service.OperatorInboxNotificationDomainService
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class ListOperatorInboxNotificationsUseCaseTest : BehaviorSpec({

    val domainService = mockk<OperatorInboxNotificationDomainService>()
    val useCase = ListOperatorInboxNotificationsUseCase(domainService)

    Given("userId=1 의 알림이 없는 상황") {
        val emptyPage = PageImpl<OperatorInboxNotification>(
            emptyList(), PageRequest.of(0, 20), 0L
        )
        every { domainService.listByRecipient(1L, null, null, any()) } returns emptyPage

        When("type 필터 없이 조회하면") {
            val command = ListOperatorInboxNotificationsCommand(
                recipientUserId = 1L,
                typeFilter = null,
                statusFilter = null,
                page = 0,
                size = 20,
            )
            val result = useCase.execute(command)

            Then("[U-01] 빈 페이지 응답이 반환된다") {
                result.totalElements shouldBe 0L
                result.content.size shouldBe 0
            }
        }
    }

    Given("UseCase 가 DomainService 를 호출하는 패턴 검증") {
        val emptyPage = PageImpl<OperatorInboxNotification>(
            emptyList(), PageRequest.of(0, 20), 0L
        )
        every { domainService.listByRecipient(1L, OperatorInboxNotificationType.ANOMALY, null, any()) } returns emptyPage

        When("type=ANOMALY 필터로 조회하면") {
            val command = ListOperatorInboxNotificationsCommand(
                recipientUserId = 1L,
                typeFilter = OperatorInboxNotificationType.ANOMALY,
                statusFilter = null,
                page = 0,
                size = 20,
            )
            useCase.execute(command)

            Then("[U-02] DomainService 에 type=ANOMALY 필터가 전달된다") {
                verify(exactly = 1) {
                    domainService.listByRecipient(1L, OperatorInboxNotificationType.ANOMALY, null, any())
                }
            }
        }
    }
})

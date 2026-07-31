package com.sportsapp.application.operator.usecase

import com.sportsapp.application.operator.dto.UpdateOperatorInboxNotificationStatusCommand
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.service.OperatorInboxNotificationDomainService
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class UpdateOperatorInboxNotificationStatusUseCaseTest : BehaviorSpec({

    val domainService = mockk<OperatorInboxNotificationDomainService>()
    val useCase = UpdateOperatorInboxNotificationStatusUseCase(domainService)

    Given("userId=1 이 소유한 UNREAD 알림이 읽음 처리된 상황") {
        val notification = mockk<OperatorInboxNotification> {
            every { id } returns 1L
            every { recipientUserId } returns 1L
            every { type } returns OperatorInboxNotificationType.ANOMALY
            every { title } returns "비정상"
            every { body } returns "내용"
            every { link } returns null
            every { status } returns OperatorInboxNotificationStatus.READ
            every { readAt } returns ZonedDateTime.now()
            every { createdAt } returns ZonedDateTime.now()
        }
        every {
            domainService.updateStatus(1L, 1L, OperatorInboxNotificationStatus.READ)
        } returns notification

        When("READ 로 상태 변경을 요청하면") {
            val command = UpdateOperatorInboxNotificationStatusCommand(
                notificationId = 1L,
                recipientUserId = 1L,
                targetStatus = OperatorInboxNotificationStatus.READ,
            )
            val result = useCase.execute(command)

            Then("[U-01] 상태가 READ 로 변경된 응답이 반환된다") {
                result.status shouldBe OperatorInboxNotificationStatus.READ
            }
        }
    }

    Given("타 사용자(userId=2) 가 userId=1 소유 알림에 접근하는 상황 (IDOR)") {
        every {
            domainService.updateStatus(1L, 2L, OperatorInboxNotificationStatus.READ)
        } throws ResourceNotFoundException("OperatorInboxNotification", 1L)

        When("userId=2 가 읽음 처리를 시도하면") {
            Then("[U-02] ResourceNotFoundException(404) 이 발생한다 — 리소스 존재 미노출") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(
                        UpdateOperatorInboxNotificationStatusCommand(
                            notificationId = 1L,
                            recipientUserId = 2L,
                            targetStatus = OperatorInboxNotificationStatus.READ,
                        )
                    )
                }
            }
        }
    }
})

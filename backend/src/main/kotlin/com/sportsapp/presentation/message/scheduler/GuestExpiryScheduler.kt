package com.sportsapp.presentation.message.scheduler

import com.sportsapp.application.message.usecase.ExpireGuestsUseCase
import com.sportsapp.application.notification.dto.SendRawNotificationCommand
import com.sportsapp.application.notification.usecase.SendRawNotificationUseCase
import com.sportsapp.domain.notification.vo.NotificationChannel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 만료된 게스트를 1분 주기로 방출한다 (TDD FR-14, `GuestEvictionDomainService.evictExpired`).
 *
 * 배치 실패 시 `NotificationChannelGateway`(원시 인터페이스는 발송 대상 `Notification` 엔티티를
 * 직접 요구해 배치 알림엔 과함)를 직접 쓰지 않고, ADR-004 교차 도메인 경로
 * (presentation → 다른 도메인 application UseCase, [AlertDeliveryEventWorker] 선례)를 따라
 * [SendRawNotificationUseCase]로 발송한다 — 내부적으로 채널별 `NotificationChannelGateway` 구현체가 선택된다.
 *
 * 롤백: `chat.guest.expiry.enabled` 플래그(코드 기본값 true, application.yml 미수정)를 false로 두면
 * 배치 실행 자체를 건너뛴다.
 */
@Component
class GuestExpiryScheduler(
    private val expireGuestsUseCase: ExpireGuestsUseCase,
    private val sendRawNotificationUseCase: SendRawNotificationUseCase,
    @Value("\${chat.guest.expiry.enabled:true}") private val expiryEnabled: Boolean,
    @Value("\${chat.guest.expiry.notify-user-id:1}") private val notifyUserId: Long,
) {
    private val log = LoggerFactory.getLogger(GuestExpiryScheduler::class.java)

    @Scheduled(cron = "0 * * * * *")
    fun evictExpiredGuests() {
        if (!expiryEnabled) {
            log.info("GuestExpiryScheduler: disabled by chat.guest.expiry.enabled flag, skipping")
            return
        }
        try {
            val evictedCount = expireGuestsUseCase.execute()
            log.info("GuestExpiryScheduler: evicted {} expired guests", evictedCount)
        } catch (exception: Exception) {
            log.error("GuestExpiryScheduler: batch failed", exception)
            notifyFailure(exception)
        }
    }

    /** 알림 채널 자체의 장애가 배치 스레드에 전파되지 않도록 감싼다 — 배치 실패 로그(위 [evictExpiredGuests])가 우선 보전된다. */
    private fun notifyFailure(exception: Exception) {
        try {
            sendRawNotificationUseCase.execute(
                SendRawNotificationCommand(
                    userId = notifyUserId,
                    channel = NotificationChannel.DISCORD,
                    templateId = TEMPLATE_ID,
                    payload = mapOf(
                        "_title" to "게스트 만료 방출 배치 실패",
                        "_body" to (exception.message ?: "unknown error"),
                    ),
                ),
            )
        } catch (notifyException: Exception) {
            log.error("GuestExpiryScheduler: failure notification itself failed", notifyException)
        }
    }

    companion object {
        private const val TEMPLATE_ID = "chat.guest-expiry-batch-failure"
    }
}

package com.sportsapp.presentation.notification.worker
import com.sportsapp.application.notification.usecase.EnqueueNotificationUseCase
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class NotificationEventWorker(
    private val enqueueNotificationUseCase: EnqueueNotificationUseCase,
) {
    @KafkaListener(topics = ["payment.completed.v1"])
    fun consumePaymentCompleted(event: PaymentCompletedEvent) {
        enqueueNotificationUseCase.execute(event.toCommand())
        enqueueNotificationUseCase.execute(event.toPushCommand())
    }

    @KafkaListener(topics = ["booking.confirmed.v1"])
    fun consumeBookingConfirmed(event: BookingConfirmedEvent) {
        enqueueNotificationUseCase.execute(event.toCommand())
        enqueueNotificationUseCase.execute(event.toPushCommand())
    }

    @KafkaListener(topics = ["ticket.issued.v1"])
    fun consumeTicketIssued(event: TicketIssuedEvent) {
        enqueueNotificationUseCase.execute(event.toCommand())
        enqueueNotificationUseCase.execute(event.toPushCommand())
    }
}

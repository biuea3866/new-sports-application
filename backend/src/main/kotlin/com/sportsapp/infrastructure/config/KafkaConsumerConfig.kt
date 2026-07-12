package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.ticketing.event.TicketEvent
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ConsumerRecordRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.kafka.support.serializer.DeserializationException
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer
import org.springframework.kafka.support.serializer.JsonDeserializer
import org.springframework.util.backoff.FixedBackOff

/**
 * Kafka Consumer 설정.
 *
 * 도메인 이벤트 프로듀서(KafkaProducerConfig)는 `ADD_TYPE_INFO_HEADERS=false`로 타입 헤더를
 * 붙이지 않는다 — 다형 역직렬화는 payload 안의 판별 프로퍼티(`eventType`)로만 이뤄진다
 * ([PaymentEvent] 등 `@JsonTypeInfo(include = EXISTING_PROPERTY)` 참고). 따라서 컨슈머 측
 * `JsonDeserializer`도 헤더에 기대지 않고(`useHeadersIfPresent = false`) 토픽별로 고정된
 * base 타입을 명시해야 한다 — 토픽마다 base 타입(도메인 이벤트 sealed class)이 다르므로
 * base 타입별로 별도의 ConsumerFactory/ContainerFactory를 둔다.
 *
 * 각 팩토리는 `ErrorHandlingDeserializer`로 key/value 역직렬화를 감싸, 손상된(poison) 레코드가
 * 무한 재시도로 컨슈머를 멈추게 하지 않고 스킵(+ 에러 로깅)되도록 한다. 리스너 UseCase가 던지는
 * 일시적 처리 예외(DB 데드락·순간 커넥션 실패 등)는 별개로 취급한다 — [kafkaErrorHandler] 참고.
 */
@Configuration
class KafkaConsumerConfig(
    @Qualifier("kafkaObjectMapper") private val objectMapper: ObjectMapper,
) {

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.consumer.group-id}")
    private lateinit var groupId: String

    @Value("\${spring.kafka.consumer.auto-offset-reset:earliest}")
    private lateinit var autoOffsetReset: String

    @Value("\${spring.kafka.consumer.properties.spring.json.trusted.packages:com.sportsapp.*}")
    private lateinit var trustedPackages: String

    private fun <T : Any> consumerFactoryFor(targetType: Class<T>): ConsumerFactory<String, T> {
        val jsonDeserializer = JsonDeserializer(targetType, objectMapper, false)
        trustedPackages.split(",").map { it.trim() }.forEach { pkg ->
            jsonDeserializer.addTrustedPackages(pkg)
        }

        val configProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
        )

        return DefaultKafkaConsumerFactory(
            configProps,
            ErrorHandlingDeserializer(StringDeserializer()),
            ErrorHandlingDeserializer(jsonDeserializer),
        )
    }

    private fun <T : Any> containerFactoryFor(targetType: Class<T>): ConcurrentKafkaListenerContainerFactory<String, T> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, T>()
        factory.consumerFactory = consumerFactoryFor(targetType)
        // Kafka 경계에서 W3C traceparent 헤더를 자동 추출해 Producer span과 동일 trace로 연결한다
        factory.containerProperties.isObservationEnabled = true
        factory.setCommonErrorHandler(kafkaErrorHandler())
        return factory
    }

    /**
     * 재시도 정책은 예외 종류로 갈린다.
     *
     * - **역직렬화 실패(poison record, [DeserializationException])** — `DefaultErrorHandler`의
     *   기본 fatal(비재시도) 목록에 이미 포함되어 있어, 아래 backoff 설정과 무관하게 즉시
     *   recoverer(로깅 + 오프셋 스킵)로 넘어간다. `addNotRetryableExceptions` 호출은 기본 목록과
     *   중복이지만, poison record가 재시도 대상이 아님을 코드로 명시하기 위해 남겨 둔다.
     * - **리스너 UseCase 처리 예외(DB 데드락·순간 커넥션 실패 등 일시적 실패)** — poison record와
     *   달리 재시도하면 성공할 수 있으므로 `FixedBackOff(1000L, 3L)`(1초 간격 최대 3회)로 재시도한다.
     *   `event.payment.payment.v1`은 "결제됨 + 주문 미확정" 상태를 막아야 하는 내구성 필수 대상이라,
     *   재시도 없이 즉시 스킵(오프셋 커밋)하면 메시지가 영구 유실된다.
     * - 재시도가 모두 소진돼도 poison record와 동일하게 recoverer가 로깅 후 오프셋을 스킵한다
     *   (DLQ 미구성 — 후속 과제).
     */
    private fun kafkaErrorHandler(): DefaultErrorHandler {
        val recoverer = ConsumerRecordRecoverer { record, exception ->
            log.error(
                "Kafka 레코드 처리 실패로 스킵합니다: topic={}, partition={}, offset={}",
                record.topic(),
                record.partition(),
                record.offset(),
                exception,
            )
        }
        val handler = DefaultErrorHandler(recoverer, FixedBackOff(1_000L, 3L))
        handler.addNotRetryableExceptions(DeserializationException::class.java)
        return handler
    }

    @Bean
    fun paymentEventKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, PaymentEvent> =
        containerFactoryFor(PaymentEvent::class.java)

    @Bean
    fun bookingEventKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, BookingEvent> =
        containerFactoryFor(BookingEvent::class.java)

    @Bean
    fun ticketEventKafkaListenerContainerFactory(): ConcurrentKafkaListenerContainerFactory<String, TicketEvent> =
        containerFactoryFor(TicketEvent::class.java)

    companion object {
        private val log = LoggerFactory.getLogger(KafkaConsumerConfig::class.java)
    }
}

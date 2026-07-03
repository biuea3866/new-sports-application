package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaProducerConfig(
    @Qualifier("kafkaObjectMapper") private val objectMapper: ObjectMapper,
) {

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.producer.acks:all}")
    private lateinit var acks: String

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        // VALUE_SERIALIZER_CLASS_CONFIG 는 setValueSerializer(인스턴스) 가 덮어쓰므로 명시하지 않음
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to acks,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
        )
        val factory = DefaultKafkaProducerFactory<String, Any>(configProps)
        factory.setValueSerializer(JsonSerializer(objectMapper))
        return factory
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> =
        KafkaTemplate(producerFactory()).apply {
            // Kafka 경계에서 W3C traceparent 헤더를 자동 주입해 Consumer span과 동일 trace로 연결한다
            setObservationEnabled(true)
        }
}

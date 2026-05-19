package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.serializer.JsonSerializer

@Configuration
class KafkaProducerConfig(
    private val objectMapper: ObjectMapper,
) {

    @Value("\${spring.kafka.bootstrap-servers}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.producer.acks:all}")
    private lateinit var acks: String

    @Bean
    fun producerFactory(): ProducerFactory<String, Any> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to acks,
            JsonSerializer.ADD_TYPE_INFO_HEADERS to false,
        )
        val factory = DefaultKafkaProducerFactory<String, Any>(configProps)
        factory.setValueSerializer(JsonSerializer(objectMapper))
        return factory
    }

    @Bean
    fun kafkaTemplate(): KafkaTemplate<String, Any> = KafkaTemplate(producerFactory())
}

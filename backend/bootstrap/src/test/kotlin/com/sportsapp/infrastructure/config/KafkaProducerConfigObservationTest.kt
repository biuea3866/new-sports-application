package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.kafka.core.KafkaTemplate

/**
 * KafkaProducerConfig — Kafka Producer 경계에서 trace 전파를 위해
 * kafkaTemplate의 observationEnabled가 켜져 있어야 한다.
 * Spring 컨텍스트 없이 구성만 검증한다(Testcontainers 경합 회피).
 */
class KafkaProducerConfigObservationTest : BehaviorSpec({

    fun newConfigWithProperties(): KafkaProducerConfig {
        val config = KafkaProducerConfig(ObjectMapper())
        setLateinitField(config, "bootstrapServers", "localhost:9092")
        setLateinitField(config, "acks", "all")
        return config
    }

    Given("KafkaProducerConfig가 초기화된 상태") {
        val config = newConfigWithProperties()

        When("kafkaTemplate을 생성하면") {
            val kafkaTemplate = config.kafkaTemplate()

            Then("observation이 활성화된다") {
                readObservationEnabled(kafkaTemplate) shouldBe true
            }
        }
    }
})

private fun setLateinitField(target: Any, fieldName: String, value: Any) {
    val field = target::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(target, value)
}

private fun readObservationEnabled(kafkaTemplate: KafkaTemplate<*, *>): Boolean {
    val field = KafkaTemplate::class.java.getDeclaredField("observationEnabled")
    field.isAccessible = true
    return field.getBoolean(kafkaTemplate)
}

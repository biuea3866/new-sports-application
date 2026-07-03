package com.sportsapp.infrastructure.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * KafkaConsumerConfig — Kafka Consumer 경계에서 trace 전파를 위해
 * kafkaListenerContainerFactory의 containerProperties.observationEnabled가 켜져 있어야 한다.
 * Spring 컨텍스트 없이 구성만 검증한다(Testcontainers 경합 회피).
 */
class KafkaConsumerConfigObservationTest : BehaviorSpec({

    fun newConfigWithProperties(): KafkaConsumerConfig {
        val config = KafkaConsumerConfig(ObjectMapper())
        setLateinitField(config, "bootstrapServers", "localhost:9092")
        setLateinitField(config, "groupId", "test-group")
        setLateinitField(config, "autoOffsetReset", "earliest")
        setLateinitField(config, "trustedPackages", "com.sportsapp.*")
        return config
    }

    Given("KafkaConsumerConfig가 초기화된 상태") {
        val config = newConfigWithProperties()

        When("kafkaListenerContainerFactory를 생성하면") {
            val factory = config.kafkaListenerContainerFactory()

            Then("containerProperties의 observation이 활성화된다") {
                factory.containerProperties.isObservationEnabled shouldBe true
            }
        }
    }
})

private fun setLateinitField(target: Any, fieldName: String, value: Any) {
    val field = target::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(target, value)
}

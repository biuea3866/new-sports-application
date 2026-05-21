package com.sportsapp.scenario

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer

/**
 * [S-01] 컨테이너 다운 후 발행 시 KafkaTemplate 이 retry + backoff 후 명확한 예외를 던진다
 *
 * Kafka 브로커 없는 환경에서 발행 시 예외가 발생함을 검증.
 * Spring Kafka 는 send().get() 호출 시 KafkaException 또는 ExecutionException 을 던진다.
 */
class KafkaFailureScenarioTest : BehaviorSpec({

    data class TestEvent(val id: Long, val name: String)

    Given("Kafka 브로커가 없는 환경(unreachable host)") {
        val unreachableBootstrapServers = "localhost:19092"

        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to unreachableBootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.MAX_BLOCK_MS_CONFIG to 2000L,
            ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG to 1000,
            ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to 2000,
            ProducerConfig.RETRIES_CONFIG to 1,
        )
        val producerFactory = DefaultKafkaProducerFactory<String, TestEvent>(producerProps)
        val kafkaTemplate = KafkaTemplate(producerFactory)

        When("도달 불가능한 브로커로 메시지 발행을 시도하면") {
            Then("[S-01] 예외가 발생하여 명확한 실패를 알린다") {
                shouldThrow<Exception> {
                    kafkaTemplate.send("fail.topic.v1", TestEvent(id = 1L, name = "fail")).get()
                }
            }
        }

        afterSpec { producerFactory.destroy() }
    }
})

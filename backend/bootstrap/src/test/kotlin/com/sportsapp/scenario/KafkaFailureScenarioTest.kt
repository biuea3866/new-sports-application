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
        // RFC 5737 TEST-NET-1(문서 전용 예약 대역) — 이 호스트의 어떤 프로세스도 이 주소로 리스닝할 수
        // 없어 "도달 불가" 전제가 환경과 무관하게 성립한다.
        //
        // 이전에는 `localhost:19092` 를 썼는데, 같은 머신의 다른 프로젝트 컨테이너가 19092 를
        // 매핑하고 있으면 발행이 **성공**해 shouldThrow 가 실패했다(실측: stock-kafka 컨테이너가
        // 0.0.0.0:19092 점유). 테스트가 통제하지 않는 호스트 상태에 의존한 결함이다.
        val unreachableBootstrapServers = "192.0.2.1:9092"

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

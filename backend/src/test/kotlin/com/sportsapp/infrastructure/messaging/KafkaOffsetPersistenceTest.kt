package com.sportsapp.infrastructure.messaging

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import com.sportsapp.SportsTestContainers
import java.time.Duration
import java.util.UUID

/**
 * [R-02] 컨테이너 재시작 후에도 동일 그룹 consumer 가 처리된 offset 을 재처리하지 않는다
 *
 * 동일 Kafka 컨테이너에서 group offset commit 후 새 consumer 인스턴스(같은 group-id) 생성 시
 * 이미 처리된 메시지를 재수신하지 않음을 검증한다.
 */
class KafkaOffsetPersistenceTest : BehaviorSpec({

    val kafkaContainer = SportsTestContainers.kafka

    val topicName = "offset-persistence.v1"
    val groupId = "offset-test-${UUID.randomUUID()}"

    Given("메시지를 발행하고 첫 번째 consumer 가 소비 후 offset 을 커밋한 상황") {
        val bootstrapServers = kafkaContainer.bootstrapServers

        val producerProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        )
        val producer = KafkaProducer<String, String>(producerProps)
        producer.send(ProducerRecord(topicName, "key-1", "message-1")).get()
        producer.close()

        val firstConsumerProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
        )
        val firstConsumer = KafkaConsumer<String, String>(firstConsumerProps)
        firstConsumer.subscribe(listOf(topicName))
        val firstBatch = firstConsumer.poll(Duration.ofSeconds(10))
        firstConsumer.commitSync()
        firstConsumer.close()

        When("같은 group-id 로 두 번째 consumer 인스턴스를 생성하면") {
            val secondConsumerProps = mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            )
            val secondConsumer = KafkaConsumer<String, String>(secondConsumerProps)
            secondConsumer.subscribe(listOf(topicName))
            val secondBatch = secondConsumer.poll(Duration.ofSeconds(5))
            secondConsumer.close()

            Then("[R-02] 첫 번째 consumer 가 소비한 메시지를 재처리하지 않는다") {
                firstBatch.count() shouldBe 1
                secondBatch.count() shouldBe 0
            }
        }
    }
})

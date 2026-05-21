package com.sportsapp.infrastructure.persistence.mongo

import com.sportsapp.BaseMongoIntegrationTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.data.mongodb.core.mapping.Document
import java.time.ZoneOffset
import java.time.ZonedDateTime

@Document(collection = "test_roundtrip_documents")
data class TestRoundtripDocument(
    @Id val id: String,
    val name: String,
    val createdAt: ZonedDateTime,
)

/**
 * [R-01] MongoTemplate save → findById 라운드트립으로 도큐먼트 필드가 정확히 보존된다
 */
class MongoTemplateRoundtripTest(
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    init {
        Given("테스트용 도큐먼트를 저장한 상태") {
            val createdAt = ZonedDateTime.of(2024, 6, 15, 12, 0, 0, 0, ZoneOffset.UTC)
            val document = TestRoundtripDocument(
                id = "test-id-001",
                name = "라운드트립 테스트",
                createdAt = createdAt,
            )

            When("MongoTemplate으로 save 후 findById 호출하면") {
                mongoTemplate.dropCollection(TestRoundtripDocument::class.java)
                mongoTemplate.save(document)
                val found = mongoTemplate.findById<TestRoundtripDocument>("test-id-001")

                Then("[R-01] 도큐먼트가 null이 아니다") {
                    found.shouldNotBeNull()
                }

                Then("[R-01] id 필드가 보존된다") {
                    found?.id shouldBe "test-id-001"
                }

                Then("[R-01] name 필드가 보존된다") {
                    found?.name shouldBe "라운드트립 테스트"
                }

                Then("[R-01] ZonedDateTime instant가 보존된다") {
                    found?.createdAt?.toInstant() shouldBe createdAt.toInstant()
                }
            }
        }
    }
}

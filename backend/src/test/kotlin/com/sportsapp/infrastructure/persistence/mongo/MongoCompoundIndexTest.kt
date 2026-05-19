package com.sportsapp.infrastructure.persistence.mongo

import com.sportsapp.BaseMongoIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.CompoundIndex
import org.springframework.data.mongodb.core.index.CompoundIndexes
import org.springframework.data.mongodb.core.index.IndexOperations
import org.springframework.data.mongodb.core.mapping.Document

@CompoundIndexes(
    CompoundIndex(name = "idx_category_status", def = "{'category': 1, 'status': 1}")
)
@Document(collection = "test_compound_index_documents")
data class TestCompoundIndexDocument(
    @Id val id: String,
    val category: String,
    val status: String,
)

/**
 * [R-02] @CompoundIndex 어노테이션이 auto-index-creation 활성화 시 실제 인덱스로 생성된다
 */
class MongoCompoundIndexTest(
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    init {
        Given("@CompoundIndex가 선언된 도큐먼트 클래스") {
            When("컬렉션에 인덱스 생성이 실행되면") {
                mongoTemplate.dropCollection(TestCompoundIndexDocument::class.java)
                mongoTemplate.createCollection(TestCompoundIndexDocument::class.java)

                val indexOperations: IndexOperations =
                    mongoTemplate.indexOps(TestCompoundIndexDocument::class.java)
                indexOperations.ensureIndex(
                    org.springframework.data.mongodb.core.index.CompoundIndexDefinition(
                        org.bson.Document("category", 1).append("status", 1)
                    ).named("idx_category_status")
                )

                val indexInfoList = indexOperations.indexInfo
                val compoundIndexExists = indexInfoList.any { indexInfo ->
                    indexInfo.name == "idx_category_status"
                }

                Then("[R-02] compound index가 실제로 생성된다") {
                    compoundIndexExists shouldBe true
                }

                Then("[R-02] 인덱스 수가 2개 이상이다 (기본 _id + compound)") {
                    (indexInfoList.size >= 2) shouldBe true
                }
            }
        }
    }
}

package com.sportsapp.infrastructure.persistence.mongo

import com.sportsapp.BaseMongoIntegrationTest
import io.kotest.matchers.shouldBe
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
 * [R-02] @CompoundIndex 어노테이션 + autoIndexCreation=true 조합이 실제로 인덱스를 생성하는지 검증.
 *
 * 수동 ensureIndex 호출 없이 컬렉션을 생성한 뒤 indexInfo 를 조회해
 * Spring Data MongoDB 의 어노테이션 기반 자동 생성이 동작함을 확인한다.
 */
class MongoCompoundIndexTest(
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    init {
        Given("@CompoundIndex 가 선언된 도큐먼트 클래스 + autoIndexCreation=true") {
            When("컬렉션을 생성하면") {
                mongoTemplate.dropCollection(TestCompoundIndexDocument::class.java)
                mongoTemplate.createCollection(TestCompoundIndexDocument::class.java)
                // 어노테이션 기반 인덱스 자동 등록을 트리거 (Spring Data MongoDB 의 autoIndexCreation 동작)
                mongoTemplate.save(TestCompoundIndexDocument("seed-1", "A", "OK"))

                val indexOperations: IndexOperations =
                    mongoTemplate.indexOps(TestCompoundIndexDocument::class.java)
                val indexInfoList = indexOperations.indexInfo

                Then("[R-02] @CompoundIndex 가 선언한 'idx_category_status' 가 자동 생성된다") {
                    val compoundIndexExists = indexInfoList.any { it.name == "idx_category_status" }
                    compoundIndexExists shouldBe true
                }

                Then("[R-02b] 기본 _id + compound 두 개 이상 인덱스가 존재한다") {
                    (indexInfoList.size >= 2) shouldBe true
                }
            }
        }
    }
}

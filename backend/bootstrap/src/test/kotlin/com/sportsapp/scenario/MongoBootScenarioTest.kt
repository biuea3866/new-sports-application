package com.sportsapp.scenario

import com.sportsapp.BaseMongoIntegrationTest
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.bson.Document
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate

/**
 * [S-01] Testcontainers MongoDB 기동 후 MongoTemplate 빈이 주입되고 ping 응답이 성공한다
 */
class MongoBootScenarioTest(
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    init {
        Given("Testcontainers MongoDB 컨테이너가 기동된 상태") {
            When("Spring 애플리케이션 컨텍스트가 로드되면") {
                Then("[S-01] MongoTemplate 빈이 주입된다") {
                    mongoTemplate.shouldBeInstanceOf<MongoTemplate>()
                }

                Then("[S-01] ping 커맨드가 1을 반환한다") {
                    val result = mongoTemplate.db.runCommand(Document("ping", 1))
                    result.getDouble("ok") shouldBe 1.0
                }
            }
        }
    }
}

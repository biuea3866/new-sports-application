package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.boot.model.naming.Identifier
import org.mockito.Mockito.mock

/**
 * [U-02] SnakeCaseStrategy — `userId` 프로퍼티가 `user_id` 컬럼명으로 변환된다
 */
class NamingStrategyTest : BehaviorSpec({

    val strategy = CamelCaseToUnderscoresNamingStrategy()
    val jdbcEnv = mock(org.hibernate.engine.jdbc.env.spi.JdbcEnvironment::class.java)

    Given("camelCase 식별자") {
        When("userId를 변환하면") {
            val result = strategy.toPhysicalColumnName(Identifier.toIdentifier("userId"), jdbcEnv)
            Then("[U-02] user_id로 변환된다") {
                result.text shouldBe "user_id"
            }
        }

        When("workspaceId를 변환하면") {
            val result = strategy.toPhysicalColumnName(Identifier.toIdentifier("workspaceId"), jdbcEnv)
            Then("[U-02] workspace_id로 변환된다") {
                result.text shouldBe "workspace_id"
            }
        }

        When("createdAt을 변환하면") {
            val result = strategy.toPhysicalColumnName(Identifier.toIdentifier("createdAt"), jdbcEnv)
            Then("[U-02] created_at으로 변환된다") {
                result.text shouldBe "created_at"
            }
        }
    }
})

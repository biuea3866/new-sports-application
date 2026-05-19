package com.sportsapp.infrastructure.persistence

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

/**
 * [R-01] Flyway — 빈 베이스라인 V1__init.sql 적용 후 flyway_schema_history에 1행이 정확히 적재된다
 */
class FlywayMigrationTest(
    @Autowired private val dataSource: DataSource,
) : BaseIntegrationTest() {

    init {
        Given("MySQL 컨테이너가 기동된 상태") {
            When("Flyway 마이그레이션이 완료되면") {
                Then("[R-01] flyway_schema_history에 정확히 1행이 존재한다") {
                    val count = dataSource.connection.use { conn ->
                        conn.prepareStatement(
                            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true"
                        ).executeQuery().use { rs ->
                            rs.next()
                            rs.getInt(1)
                        }
                    }
                    count shouldBe 1
                }
            }
        }
    }
}

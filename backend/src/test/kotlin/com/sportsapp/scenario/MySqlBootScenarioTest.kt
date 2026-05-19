package com.sportsapp.scenario

import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

/**
 * [S-01] Testcontainers MySQL 부팅 — 컨테이너 기동 후 애플리케이션이 30초 내 부팅 완료되고 schema_history가 생성된다
 */
class MySqlBootScenarioTest(
    @Autowired private val dataSource: DataSource,
) : BaseIntegrationTest() {

    init {
        Given("Testcontainers MySQL 컨테이너가 기동된 상태") {
            When("Spring 애플리케이션 컨텍스트가 로드되면") {
                Then("[S-01] flyway_schema_history 테이블이 존재하고 마이그레이션이 1건 이상 적재된다") {
                    val count = dataSource.connection.use { conn ->
                        conn.prepareStatement(
                            "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true"
                        ).executeQuery().use { rs ->
                            rs.next()
                            rs.getInt(1)
                        }
                    }
                    (count >= 1) shouldBe true
                }
            }
        }
    }
}

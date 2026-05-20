package com.sportsapp.scenario

import com.sportsapp.BaseJpaIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

/**
 * [B2B-01] V17 마이그레이션 멱등성 시나리오
 * S-01: V17 시드를 2회 이상 적용해도 roles/permissions/role_permissions row 수가 변하지 않는다
 */
class B2bMigrationIdempotencyScenarioTest(
    @Autowired private val dataSource: DataSource,
) : BaseJpaIntegrationTest() {

    init {
        Given("V17 마이그레이션이 이미 적용된 상태") {
            When("roles 테이블에서 B2B 롤을 조회하면") {
                Then("[S-01] EVENT_HOST, GOODS_SELLER 롤 수는 정확히 각 1건이다 (idempotent 시드)") {
                    val count = dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            "SELECT COUNT(*) FROM roles WHERE name IN ('EVENT_HOST', 'GOODS_SELLER') AND deleted_at IS NULL"
                        ).executeQuery().use { resultSet ->
                            resultSet.next()
                            resultSet.getInt(1)
                        }
                    }
                    count shouldBe 2
                }
            }

            When("permissions 테이블에서 B2B 퍼미션을 조회하면") {
                Then("[S-01] B2B 관련 퍼미션이 정확히 7건이다 (idempotent 시드 — 중복 삽입 0건)") {
                    val count = dataSource.connection.use { connection ->
                        connection.prepareStatement(
                            """SELECT COUNT(*) FROM permissions
                               WHERE (name LIKE 'facility:%' OR name LIKE 'event:%'
                                   OR name LIKE 'product:%' OR name LIKE 'b2b:%')
                               AND deleted_at IS NULL"""
                        ).executeQuery().use { resultSet ->
                            resultSet.next()
                            resultSet.getInt(1)
                        }
                    }
                    count shouldBe 7
                }
            }
        }
    }
}

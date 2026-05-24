package com.sportsapp.infrastructure.persistence

import com.sportsapp.BaseJpaIntegrationTest
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import javax.sql.DataSource

/**
 * [T06] V25 마이그레이션 — tickets.ticket_order_id NULL 전환 + sentinel 0L backfill 검증
 *
 * R-01: Flyway 적용 후 ticket_order_id 컬럼이 nullable로 전환된다
 * R-02: sentinel 0 row가 NULL로 정정된다
 * R-03: sentinel 외 정상 ticket_order_id(> 0) row는 영향받지 않는다
 */
class V25MigrationTest(
    @Autowired private val dataSource: DataSource,
) : BaseJpaIntegrationTest() {

    init {
        Given("V25 마이그레이션이 적용된 MySQL 컨테이너") {
            When("information_schema.columns 로 ticket_order_id 컬럼 속성을 조회하면") {
                Then("[R-01] ticket_order_id 컬럼이 nullable(IS_NULLABLE = YES)로 전환된다") {
                    val isNullable = dataSource.connection.use { conn ->
                        conn.prepareStatement(
                            """
                            SELECT IS_NULLABLE
                            FROM information_schema.columns
                            WHERE table_schema = DATABASE()
                              AND table_name    = 'tickets'
                              AND column_name   = 'ticket_order_id'
                            """
                        ).executeQuery().use { rs ->
                            rs.next()
                            rs.getString("IS_NULLABLE")
                        }
                    }
                    isNullable shouldBe "YES"
                }
            }

            When("sentinel 0L row를 INSERT한 뒤 V25 backfill 로직(UPDATE)을 재실행하면") {
                Then("[R-02] sentinel 0 row가 NULL로 정정되고, sentinel 외 row는 그대로 유지된다") {
                    dataSource.connection.use { conn ->
                        // 테스트 픽스처: sentinel row 1건 + 정상 row 1건 삽입
                        conn.prepareStatement(
                            """
                            INSERT INTO tickets (ticket_order_id, seat_id, status, code, created_at, updated_at)
                            VALUES (0, 9001, 'ISSUED', 'SENTINEL_TEST_CODE_V25_00000001', NOW(6), NOW(6)),
                                   (42, 9002, 'ISSUED', 'NORMAL_TEST_CODE_V25_000000001', NOW(6), NOW(6))
                            """
                        ).executeUpdate()

                        // V25 backfill SQL 재실행 (멱등성 검증)
                        conn.prepareStatement(
                            "UPDATE tickets SET ticket_order_id = NULL WHERE ticket_order_id = 0"
                        ).executeUpdate()

                        // R-02: sentinel row → NULL
                        val sentinelNullCount = conn.prepareStatement(
                            """
                            SELECT COUNT(*) AS cnt
                            FROM tickets
                            WHERE ticket_order_id IS NULL
                              AND code = 'SENTINEL_TEST_CODE_V25_00000001'
                            """
                        ).executeQuery().use { rs ->
                            rs.next()
                            rs.getInt("cnt")
                        }
                        sentinelNullCount shouldBe 1

                        // R-03: 정상 row(ticket_order_id = 42)는 변경되지 않는다
                        val normalRowCount = conn.prepareStatement(
                            """
                            SELECT COUNT(*) AS cnt
                            FROM tickets
                            WHERE ticket_order_id = 42
                              AND code = 'NORMAL_TEST_CODE_V25_000000001'
                            """
                        ).executeQuery().use { rs ->
                            rs.next()
                            rs.getInt("cnt")
                        }
                        normalRowCount shouldBe 1

                        // 테스트 픽스처 정리
                        conn.prepareStatement(
                            """
                            DELETE FROM tickets
                            WHERE code IN ('SENTINEL_TEST_CODE_V25_00000001', 'NORMAL_TEST_CODE_V25_000000001')
                            """
                        ).executeUpdate()
                    }
                }
            }
        }
    }
}

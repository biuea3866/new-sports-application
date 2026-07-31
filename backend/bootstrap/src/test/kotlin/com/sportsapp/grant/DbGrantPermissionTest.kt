package com.sportsapp.grant

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import org.testcontainers.containers.MySQLContainer

/**
 * [W1-04] 서비스별 DB 유저 + 테이블 단위 GRANT 물리 차단 검증.
 *
 * 근거: 아키텍트/20260728-msa-물리분리-실행설계.md §3-1(GRANT 물리 차단) §2-1(45테이블 소유권 배분표).
 *
 * 이 스펙은 **자기 전용 disposable MySQLContainer**를 `beforeSpec`에서 기동하고 `afterSpec`에서
 * 종료한다 — [com.sportsapp.SharedTestContainers.mysql](JVM 싱글톤 공유 컨테이너)을 쓰지 않는다.
 * (리뷰 p1 후속) 이 스펙의 관심사는 "DB 서버 레벨 권한"뿐이라 공유가 이득이 없고, 오히려 공유
 * 컨테이너를 오염시킨다:
 *   ① [createMinimalSchema]가 Flyway를 거치지 않고 스텁 테이블 54개를 만들면, 같은 JVM에서 나중에
 *      기동하는 다른 `@SpringBootTest`가 `flyway.enabled=true`(bootstrap 테스트 설정) 상태에서
 *      "Found non-empty schema(s) without schema history table"로 컨텍스트 기동에 실패한다.
 *   ② `createMinimalSchema`가 만드는 스텁 `BATCH_JOB_INSTANCE`(마커 테이블)를
 *      [com.sportsapp.infrastructure.config.BatchMetadataSchemaInitializer]가 "이미 초기화됨"으로
 *      오판해, 실제 Spring Batch 스키마 생성을 **영구 스킵**한다 — 순서와 무관하게 항상 발생하는
 *      문제였다(실제 BATCH_* 컬럼이 없는 스텁 테이블 위에서 배치 테스트가 실행돼 실패).
 * 전용 컨테이너로 격리하면 두 문제 모두 원천 차단된다 — 이 스펙이 만드는 스텁 스키마는 이
 * 컨테이너 안에서만 존재하고 다른 스펙과 공유되지 않는다.
 *
 * 권한 거부는 SQLException 발생 여부만이 아니라 **MySQL 권한 거부 에러 코드**(1142/1044/1143)로
 * 판별한다 — 단순 SQLException만 확인하면 "유저가 아예 없어서 인증에서 실패"(에러 1045)한 경우에도
 * 테스트가 통과해버리는 false-green을 방지하기 위함이다 (GRANT가 실제로 적용됐는지가 아니라
 * 유저 존재 여부만 우연히 검증하게 되는 사고를 막는다).
 *
 * [createMinimalSchema]가 45개 도메인 테이블 + 9개 BATCH_* 테이블을 전부 만든 뒤에만
 * `02-grants.sql`을 실행한다 — MySQL은 존재하지 않는 테이블에 대한 GRANT를 ERROR 1146으로
 * 거부하므로(재현 확인됨), 실제 운영에서도 이 스크립트는 backend가 최초 기동을 마쳐 전 테이블이
 * 생성된 뒤에만 적용 가능하다. 이 테스트는 그 전제를 그대로 재현해 검증한다.
 */
class DbGrantPermissionTest : FunSpec({

    lateinit var mysql: MySQLContainer<*>

    fun jdbcUrl(): String =
        "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(3306)}/sports?useSSL=false&allowPublicKeyRetrieval=true"

    fun rootConnection(): Connection = DriverManager.getConnection(jdbcUrl(), "root", "test")

    fun connectAs(username: String, password: String): Connection =
        DriverManager.getConnection(jdbcUrl(), username, password)

    fun findGrantsSql(): File {
        tailrec fun search(dir: File?): File? {
            val currentDir = dir ?: return null
            val candidate = File(currentDir, "infra/mysql/grants/02-grants.sql")
            if (candidate.exists()) return candidate
            return search(currentDir.parentFile)
        }
        return search(File(System.getProperty("user.dir")).absoluteFile)
            ?: error("infra/mysql/grants/02-grants.sql 을 찾을 수 없습니다 (user.dir=${System.getProperty("user.dir")})")
    }

    fun runSqlFile(connection: Connection, file: File) {
        val statements = file.readText()
            .lines()
            .filterNot { it.trim().startsWith("--") }
            .joinToString("\n")
            .split(";")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        connection.createStatement().use { statement ->
            statements.forEach { sql -> statement.execute(sql) }
        }
    }

    // 02-grants.sql이 실제로 GRANT하는 45개 도메인 테이블 전부 — 하나라도 없으면 그 테이블의
    // GRANT 문에서 ERROR 1146(테이블 없음)이 발생해 스크립트 실행이 중단된다(운영에서도 동일하게
    // backend 최초 기동으로 전 테이블이 생성된 뒤에만 apply-grants.sh를 실행해야 하는 이유와 같다).
    val domainTables = listOf(
        "alerts", "applications", "bookings", "cart_items", "carts", "comments", "communities",
        "community_bookings", "community_members", "events", "feature_flag_audit_logs", "feature_flags",
        "goods_order_items", "goods_orders", "limited_drops", "mcp_anomaly_events", "mcp_audit_logs",
        "mcp_token_scopes", "mcp_tokens", "messages", "notifications", "operator_inbox_notifications",
        "partner", "partner_api_key", "partner_audit_log", "payments", "permissions", "posts", "products",
        "programs", "push_tokens", "recruitments", "regions", "role_permissions", "roles",
        "room_invitations", "room_participants", "rooms", "seats", "slots", "stocks", "ticket_orders",
        "tickets", "user_roles", "users",
    )

    // Spring Batch 메타 테이블 — 도메인 마이그레이션이 아니라 앱 최초 기동 시
    // BatchMetadataSchemaInitializer가 만든다(svc_commerce 소유, §2-2 D2).
    val batchTables = listOf(
        "BATCH_JOB_INSTANCE", "BATCH_JOB_EXECUTION", "BATCH_JOB_EXECUTION_PARAMS",
        "BATCH_JOB_EXECUTION_CONTEXT", "BATCH_STEP_EXECUTION", "BATCH_STEP_EXECUTION_CONTEXT",
        "BATCH_JOB_EXECUTION_SEQ", "BATCH_STEP_EXECUTION_SEQ", "BATCH_JOB_SEQ",
    )

    fun createMinimalSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            (domainTables + batchTables).forEach { table ->
                statement.execute(
                    "CREATE TABLE IF NOT EXISTS $table (id BIGINT PRIMARY KEY AUTO_INCREMENT, name VARCHAR(50))",
                )
            }
        }
    }

    // MySQL 권한 거부 에러 코드: 1142(ER_TABLEACCESS_DENIED_ERROR), 1044(ER_DBACCESS_DENIED_ERROR),
    // 1143(ER_COLUMNACCESS_DENIED_ERROR). 1045(ER_ACCESS_DENIED_ERROR, 로그인 실패)는 제외한다.
    val privilegeDeniedErrorCodes = setOf(1142, 1044, 1143)

    fun shouldDenyByPrivilege(block: () -> Unit) {
        val exception = shouldThrow<SQLException> { block() }
        privilegeDeniedErrorCodes shouldContain exception.errorCode
    }

    beforeSpec {
        mysql = MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
        mysql.start()
        rootConnection().use { root ->
            createMinimalSchema(root)
            runSqlFile(root, findGrantsSql())
        }
    }

    afterSpec {
        mysql.stop()
    }

    test("svc_social 이 products 를 SELECT하면 권한 거부 오류가 발생한다") {
        connectAs("svc_social", "svc_social_pw").use { conn ->
            shouldDenyByPrivilege {
                conn.createStatement().executeQuery("SELECT * FROM products")
            }
        }
    }

    test("svc_payment 가 products 를 SELECT하면 권한 거부 오류가 발생한다") {
        connectAs("svc_payment", "svc_payment_pw").use { conn ->
            shouldDenyByPrivilege {
                conn.createStatement().executeQuery("SELECT * FROM products")
            }
        }
    }

    test("svc_commerce 가 payments 를 SELECT하면 권한 거부 오류가 발생한다") {
        connectAs("svc_commerce", "svc_commerce_pw").use { conn ->
            shouldDenyByPrivilege {
                conn.createStatement().executeQuery("SELECT * FROM payments")
            }
        }
    }

    test("svc_facility_booking 이 messages 를 SELECT하면 권한 거부 오류가 발생한다") {
        connectAs("svc_facility_booking", "svc_facility_booking_pw").use { conn ->
            shouldDenyByPrivilege {
                conn.createStatement().executeQuery("SELECT * FROM messages")
            }
        }
    }

    test("svc_platform 이 bookings 를 SELECT하면 권한 거부 오류가 발생한다") {
        connectAs("svc_platform", "svc_platform_pw").use { conn ->
            shouldDenyByPrivilege {
                conn.createStatement().executeQuery("SELECT * FROM bookings")
            }
        }
    }

    test("svc_edge 는 어떤 테이블도 SELECT할 수 없다 (권한 0)") {
        // svc_edge는 sports 스키마에 권한이 전혀 없어 SELECT 실행 이전에 연결(USE sports) 자체가
        // ERROR 1044(DB 접근 거부)로 거부된다 — connectAs 호출도 shouldDenyByPrivilege 안에 둔다.
        shouldDenyByPrivilege {
            connectAs("svc_edge", "svc_edge_pw").use { conn ->
                conn.createStatement().executeQuery("SELECT * FROM products")
            }
        }
    }

    // 자기 소유 테이블 SELECT·INSERT·UPDATE·DELETE 4개 권한이 실제로 "실행 성공"하는지 검증한다
    // (리뷰 p2⑤ 후속 — 전용 컨테이너로 격리했으므로 다른 스펙이 만든 실제 Flyway 스키마와
    // 충돌할 걱정 없이, createMinimalSchema가 만든 스텁 컬럼(id, name)으로 직접 CRUD를 실행한다).
    fun verifyOwnTableAllPrivileges(username: String, password: String, table: String) {
        connectAs(username, password).use { conn ->
            conn.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM $table").use { resultSet ->
                    resultSet.next() shouldBe true
                }
            }
            conn.prepareStatement("INSERT INTO $table (name) VALUES (?)").use { statement ->
                statement.setString(1, "grant-test-$table")
                statement.executeUpdate() shouldBe 1
            }
            conn.createStatement().use { statement ->
                statement.executeUpdate(
                    "UPDATE $table SET name = 'grant-test-updated' WHERE name = 'grant-test-$table'",
                ) shouldBe 1
            }
            conn.createStatement().use { statement ->
                statement.executeUpdate(
                    "DELETE FROM $table WHERE name = 'grant-test-updated'",
                ) shouldBe 1
            }
        }
    }

    test("svc_commerce 는 자기 소유 테이블 products 에 SELECT·INSERT·UPDATE·DELETE 를 성공한다") {
        verifyOwnTableAllPrivileges("svc_commerce", "svc_commerce_pw", "products")
    }

    test("svc_payment 는 자기 소유 테이블 payments 에 SELECT·INSERT·UPDATE·DELETE 를 성공한다") {
        verifyOwnTableAllPrivileges("svc_payment", "svc_payment_pw", "payments")
    }

    test("svc_facility_booking 은 자기 소유 테이블 bookings 에 SELECT·INSERT·UPDATE·DELETE 를 성공한다") {
        verifyOwnTableAllPrivileges("svc_facility_booking", "svc_facility_booking_pw", "bookings")
    }

    test("svc_social 은 자기 소유 테이블 posts 에 SELECT·INSERT·UPDATE·DELETE 를 성공한다") {
        verifyOwnTableAllPrivileges("svc_social", "svc_social_pw", "posts")
    }

    test("svc_platform 은 자기 소유 테이블 users 에 SELECT·INSERT·UPDATE·DELETE 를 성공한다") {
        verifyOwnTableAllPrivileges("svc_platform", "svc_platform_pw", "users")
    }

    test("앱 유저(svc_commerce)에게 DDL(CREATE TABLE) 권한이 없다") {
        connectAs("svc_commerce", "svc_commerce_pw").use { conn ->
            shouldDenyByPrivilege {
                conn.createStatement().execute("CREATE TABLE ddl_probe_commerce (id BIGINT)")
            }
        }
    }

    test("flyway_migrator 는 DDL(CREATE/DROP TABLE) 권한을 보유한다") {
        connectAs("flyway_migrator", "flyway_migrator_pw").use { conn ->
            conn.createStatement().use { statement ->
                statement.execute("CREATE TABLE IF NOT EXISTS ddl_probe_flyway (id BIGINT)")
                statement.execute("DROP TABLE ddl_probe_flyway")
            }
        }
    }

    test("svc_social 이 소유 테이블(posts)과 남의 테이블(products)을 JOIN 하면 권한 거부 오류가 발생한다") {
        connectAs("svc_social", "svc_social_pw").use { conn ->
            shouldDenyByPrivilege {
                conn.createStatement().executeQuery(
                    "SELECT p.id FROM posts p JOIN products pr ON p.id = pr.id",
                )
            }
        }
    }

    test("02-grants.sql 을 두 번 실행해도 svc_commerce 의 최종 권한 개수가 동일하다 (멱등)") {
        rootConnection().use { root ->
            runSqlFile(root, findGrantsSql())
            val firstCount = countTablePrivileges(root, "svc_commerce")

            runSqlFile(root, findGrantsSql())
            val secondCount = countTablePrivileges(root, "svc_commerce")

            // svc_commerce: 11 도메인 테이블 + BATCH_* 9 테이블 = 20 × 4 권한(SELECT/INSERT/UPDATE/DELETE) = 80
            firstCount shouldBe 80
            secondCount shouldBe firstCount
        }
    }

    // 6개 서비스 유저 전부의 최종 테이블 권한 개수를 기대값과 대조한다 (리뷰 p3 후속) — 기존에는
    // svc_commerce 1건만 단언했고, svc_edge의 "권한 0"이라는 경계의 핵심 주장이 SELECT 연결 거부
    // 테스트로만 간접 검증됐다. apply-grants.sh 의 verify 모드가 쓰는 기대값과 동일하다.
    test("6개 서비스 유저의 최종 테이블 권한 개수가 각각 기대값과 일치한다") {
        rootConnection().use { root ->
            countTablePrivileges(root, "svc_commerce") shouldBe 80
            countTablePrivileges(root, "svc_payment") shouldBe 4
            countTablePrivileges(root, "svc_facility_booking") shouldBe 16
            countTablePrivileges(root, "svc_social") shouldBe 44
            countTablePrivileges(root, "svc_platform") shouldBe 72
            countTablePrivileges(root, "svc_edge") shouldBe 0
        }
    }

    // 테이블 목록이 ① 마이그레이션 ② 02-grants.sql ③ 이 스펙의 하드코딩 배열, 3곳에 중복돼 있어
    // 46번째 테이블이 추가돼도 GRANT 누락이 아무것도 실패시키지 않는 드리프트를 막는다(리뷰 p2③
    // 후속). information_schema.tables(실제 존재하는 테이블) ⟂ information_schema.table_privileges
    // (실제 부여된 GRANT)를 대조해 "소유자 없는 테이블 0건"을 단언한다 — 하드코딩 배열이 아니라
    // 스키마 자체를 근거로 삼으므로, 향후 새 테이블이 02-grants.sql 갱신 없이 추가되면 이 테스트가
    // 즉시 실패한다. flyway_migrator는 스키마 단위(sports.*) GRANT라 table_privileges에 잡히지
    // 않으므로 정상적으로 제외한다.
    test("GRANT 스크립트가 다루는 모든 테이블에 소유 서비스 유저가 있다 (테이블 집합 드리프트 가드)") {
        rootConnection().use { root ->
            val ownerlessTables = mutableListOf<String>()
            root.createStatement().use { statement ->
                statement.executeQuery(
                    """
                    SELECT t.table_name
                    FROM information_schema.tables t
                    WHERE t.table_schema = 'sports'
                      AND t.table_name NOT LIKE 'ddl_probe_%'
                      AND NOT EXISTS (
                        SELECT 1 FROM information_schema.table_privileges p
                        WHERE p.table_schema = t.table_schema
                          AND p.table_name = t.table_name
                          AND p.grantee != "'flyway_migrator'@'%'"
                      )
                    """.trimIndent(),
                ).use { resultSet ->
                    while (resultSet.next()) {
                        ownerlessTables += resultSet.getString("table_name")
                    }
                }
            }
            ownerlessTables shouldBe emptyList()
        }
    }
})

private fun countTablePrivileges(connection: Connection, grantUsername: String): Int =
    connection.createStatement().use { statement ->
        val resultSet = statement.executeQuery(
            "SELECT COUNT(*) FROM information_schema.table_privileges " +
                "WHERE grantee = \"'$grantUsername'@'%'\"",
        )
        resultSet.next()
        resultSet.getInt(1)
    }

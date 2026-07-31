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
 * [createMinimalSchema]가 마이그레이션 파일에서 파생한 도메인 테이블 전부(현재 45개) + 9개
 * BATCH_* 테이블을 만든 뒤에만 `02-grants.sql`을 실행한다 — MySQL은 존재하지 않는 테이블에 대한
 * GRANT를 ERROR 1146으로 거부하므로(재현 확인됨), 실제 운영에서도 이 스크립트는 backend가 최초
 * 기동을 마쳐 전 테이블이 생성된 뒤에만 적용 가능하다. 이 테스트는 그 전제를 그대로 재현해
 * 검증한다. 이 스펙의 관심사는 어디까지나 "권한 거부/허용이 실제로 작동하는가"이고, 마이그레이션·
 * 02-grants.sql 두 원본 사이의 테이블 집합 드리프트는 [MigrationGrantParityTest](컨테이너 없는
 * 파일 레벨 비교)가 검증한다 — 이 스펙의 information_schema 대조(아래 "GRANT 스크립트가 다루는
 * 모든 테이블에 소유 서비스 유저가 있다" 테스트)는 이 스펙 자신이 만든 스텁 스키마를 대상으로 할
 * 뿐, 실제 운영 스키마와의 드리프트를 잡지 않는다.
 */
class DbGrantPermissionTest : FunSpec({

    lateinit var mysql: MySQLContainer<*>

    fun jdbcUrl(): String =
        "jdbc:mysql://${mysql.host}:${mysql.getMappedPort(3306)}/sports?useSSL=false&allowPublicKeyRetrieval=true"

    fun rootConnection(): Connection = DriverManager.getConnection(jdbcUrl(), "root", "test")

    fun connectAs(username: String, password: String): Connection =
        DriverManager.getConnection(jdbcUrl(), username, password)

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

    // 02-grants.sql이 실제로 GRANT하는 도메인 테이블 전부 — 하나라도 없으면 그 테이블의
    // GRANT 문에서 ERROR 1146(테이블 없음)이 발생해 스크립트 실행이 중단된다(운영에서도 동일하게
    // backend 최초 기동으로 전 테이블이 생성된 뒤에만 apply-grants.sh를 실행해야 하는 이유와 같다).
    // [재리뷰 p2① 후속] 이전에는 이 테이블 목록을 하드코딩 배열로 들고 있어 마이그레이션에 테이블이
    // 추가돼도 이 배열을 손으로 갱신하지 않는 한 스텁 스키마가 실제와 어긋난 채 조용히 통과했다.
    // 이제는 실제 마이그레이션 SQL 파일을 파싱해([GrantSchemaFiles.tablesCreatedByMigrations])
    // 이 스펙의 스텁 스키마가 항상 최신 마이그레이션과 같은 테이블 집합을 갖도록 파생시킨다 —
    // 마이그레이션·02-grants.sql 두 원본 사이의 실제 드리프트 검출은 [MigrationGrantParityTest]가
    // 담당한다(컨테이너 없이 더 빠르게 검증).
    val domainTables = GrantSchemaFiles.tablesCreatedByMigrations().toList()

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
            runSqlFile(root, GrantSchemaFiles.grantsSqlFile())
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
            runSqlFile(root, GrantSchemaFiles.grantsSqlFile())
            val firstCount = countTablePrivileges(root, "svc_commerce")

            runSqlFile(root, GrantSchemaFiles.grantsSqlFile())
            val secondCount = countTablePrivileges(root, "svc_commerce")

            // [재리뷰 p3 후속] 기대값(80)을 하드코딩하지 않고 02-grants.sql 에서 파생한다
            // (GrantSchemaFiles.expectedTablePrivilegeCount — svc_commerce: 11 도메인 + BATCH_* 9
            // = 20 GRANT 라인 × 4권한). apply-grants.sh 의 expected_count_for_user() 와 동일한 산식.
            firstCount shouldBe GrantSchemaFiles.expectedTablePrivilegeCount("svc_commerce")
            secondCount shouldBe firstCount
        }
    }

    // 6개 서비스 유저 전부의 최종 테이블 권한 개수를 기대값과 대조한다 (리뷰 p3 후속) — 기존에는
    // svc_commerce 1건만 단언했고, svc_edge의 "권한 0"이라는 경계의 핵심 주장이 SELECT 연결 거부
    // 테스트로만 간접 검증됐다. [재리뷰 p3 후속] 기대값은 하드코딩 숫자가 아니라 02-grants.sql 에서
    // 파생한다(GrantSchemaFiles.expectedTablePrivilegeCount) — apply-grants.sh 의 verify 모드가
    // 쓰는 산식과 동일한 소스를 공유해, 02-grants.sql이 바뀌어도 두 곳을 손으로 맞출 필요가 없다.
    test("6개 서비스 유저의 최종 테이블 권한 개수가 각각 기대값과 일치한다") {
        rootConnection().use { root ->
            countTablePrivileges(root, "svc_commerce") shouldBe GrantSchemaFiles.expectedTablePrivilegeCount("svc_commerce")
            countTablePrivileges(root, "svc_payment") shouldBe GrantSchemaFiles.expectedTablePrivilegeCount("svc_payment")
            countTablePrivileges(root, "svc_facility_booking") shouldBe GrantSchemaFiles.expectedTablePrivilegeCount("svc_facility_booking")
            countTablePrivileges(root, "svc_social") shouldBe GrantSchemaFiles.expectedTablePrivilegeCount("svc_social")
            countTablePrivileges(root, "svc_platform") shouldBe GrantSchemaFiles.expectedTablePrivilegeCount("svc_platform")
            countTablePrivileges(root, "svc_edge") shouldBe GrantSchemaFiles.expectedTablePrivilegeCount("svc_edge")
        }
    }

    // [재리뷰 p2① 후속 — 서술 정정] 이 테스트는 information_schema.tables ⟂ information_schema.
    // table_privileges 를 대조하지만, 대조 대상인 information_schema.tables 자체가 이 스펙의
    // beforeSpec 이 만든 스텁 스키마([createMinimalSchema], 위 domainTables 참고)다. domainTables는
    // 이제 하드코딩 배열이 아니라 마이그레이션 파일을 파싱해 파생하므로(GrantSchemaFiles), 이
    // 테스트는 "그 파생된 스텁 스키마 안에서 GRANT 누락이 없는가"를 검증한다 — 즉 02-grants.sql이
    // domainTables(=마이그레이션에서 파생된 값)와 실제로 어긋나지 않는지의 **간접** 확인이다.
    // 구 버전 주석은 "스키마 자체를 근거로 삼으므로 향후 새 테이블이 갱신 없이 추가되면 즉시
    // 실패한다"고 서술했으나 사실이 아니었다 — domainTables가 하드코딩이던 시절에는 컨테이너
    // 기동조차 없이는 드리프트가 절대 드러나지 않았다. 컨테이너 없이 마이그레이션·02-grants.sql
    // 두 실제 원본을 직접(양방향) 대조하는 결정적 가드는 [MigrationGrantParityTest]가 수행한다.
    test("GRANT 스크립트가 다루는 모든 테이블에 소유 서비스 유저가 있다 (스텁 스키마 내부 정합성)") {
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

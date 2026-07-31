package com.sportsapp.grant

import java.io.File

/**
 * [W1-04][재리뷰 p2① 후속] Flyway 마이그레이션 SQL과 infra/mysql/grants/02-grants.sql —
 * 두 **실제 원본** 파일에서 테이블 집합을 직접 파싱하는 공용 유틸리티.
 *
 * 이전에는 [DbGrantPermissionTest]가 45개 테이블명을 하드코딩 배열(domainTables)로 들고 있어,
 * 마이그레이션에 46번째 테이블이 추가돼도 02-grants.sql·이 배열 양쪽에 손으로 추가하지 않는 한
 * 아무 테스트도 실패하지 않았다(거짓 방어 — 대조 대상인 information_schema.tables 자체가 이
 * 하드코딩 배열로 만든 스텁이었다). 이 유틸리티는 대조 대상을 파일 시스템의 실제 원본으로
 * 바꿔, 두 원본이 어긋나면 드리프트 가드가 실제로 실패하게 한다.
 */
object GrantSchemaFiles {

    private val createTableRegex = Regex(
        """CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?([a-zA-Z0-9_]+)`?""",
        RegexOption.IGNORE_CASE,
    )

    private val grantTableRegex = Regex(
        """ON\s+sports\.([a-zA-Z0-9_]+)\s+TO\s+'([a-zA-Z0-9_]+)'@'%'""",
        RegexOption.IGNORE_CASE,
    )

    /** infra/mysql/grants/02-grants.sql 이 존재하는 디렉터리를 상위로 거슬러 올라가며 찾는다. */
    private tailrec fun findRepoRoot(dir: File?): File {
        val currentDir = dir ?: error(
            "리포지토리 루트를 찾을 수 없습니다 (infra/mysql/grants/02-grants.sql 기준, user.dir=${System.getProperty("user.dir")})",
        )
        val candidate = File(currentDir, "infra/mysql/grants/02-grants.sql")
        if (candidate.exists()) return currentDir
        return findRepoRoot(currentDir.parentFile)
    }

    fun repoRoot(): File = findRepoRoot(File(System.getProperty("user.dir")).absoluteFile)

    fun grantsSqlFile(): File = File(repoRoot(), "infra/mysql/grants/02-grants.sql")

    fun migrationDir(): File = File(repoRoot(), "backend/bootstrap/src/main/resources/db/migration")

    /**
     * 마이그레이션 SQL 전체에서 `CREATE TABLE`로 생성된 테이블명 집합.
     * BATCH_* 테이블은 마이그레이션이 아니라 BatchMetadataSchemaInitializer(앱 최초 기동)가
     * 만들므로 여기 포함되지 않는다 — 02-grants.sql GRANT 집합과 대조할 때도 그쪽에서 제외한다.
     */
    fun tablesCreatedByMigrations(): Set<String> =
        migrationDir()
            .listFiles { file -> file.extension.equals("sql", ignoreCase = true) }
            .orEmpty()
            .flatMap { file -> createTableRegex.findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()

    private val grantPrivilegeRegex = Regex(
        """GRANT\s+([A-Za-z,\s]+?)\s+ON\s+sports\.([a-zA-Z0-9_]+)\s+TO\s+'([a-zA-Z0-9_]+)'@'%'""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 02-grants.sql 이 테이블 단위로 GRANT하는 대상 → 그 테이블에 GRANT된 유저 목록
     * (BATCH_* 제외, flyway_migrator의 `ON sports.*` 스키마 단위 GRANT도 이 정규식에 매칭되지
     * 않아 자연히 제외된다). §2-1의 "한 테이블 = 한 서비스" 원칙상 값(List<유저>)의 크기는
     * 항상 1이어야 한다 — [MigrationGrantParityTest]의 "테이블마다 소유 유저가 정확히 1명이다"
     * 테스트가 이 값으로 단일 소유를 직접 가드한다(재리뷰 p3-1 후속. 이전에는 이 함수가
     * Set<String>만 반환해 어떤 테스트도 "같은 테이블에 두 유저가 GRANT됐는가"를 볼 수
     * 없었다 — parity 두 테스트는 집합 기준이라 통과하고, expectedTablePrivilegeCount는 이
     * 파일 자신에서 파생돼 자기 자신에 맞춰 늘어나므로 통과했다).
     */
    fun tableOwnersInGrantsSql(): Map<String, List<String>> =
        grantTableRegex.findAll(grantsSqlFile().readText())
            .map { it.groupValues[1] to it.groupValues[2] }
            .filterNot { (table, _) -> table.startsWith("BATCH_") }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

    /**
     * 02-grants.sql 이 테이블 단위로 GRANT하는 대상 (BATCH_* 제외, flyway_migrator의
     * `ON sports.*` 스키마 단위 GRANT도 이 정규식에 매칭되지 않아 자연히 제외된다).
     */
    fun tablesGrantedInGrantsSql(): Set<String> = tableOwnersInGrantsSql().keys

    /**
     * 02-grants.sql 의 테이블 단위 GRANT 문(BATCH_* 포함, `ON sports.*` 스키마 단위 GRANT는
     * 정규식에 매칭되지 않아 제외)이 실제로 부여하는 권한 집합 — 테이블명 → 권한 Set.
     * [expectedTablePrivilegeCount]·scripts/apply-grants.sh의 expected_count_for_user() 는
     * 둘 다 "GRANT 라인 수 × 4"로 계산한다 — 즉 "이 파일의 모든 테이블 단위 GRANT가 예외 없이
     * SELECT/INSERT/UPDATE/DELETE 4권한을 부여한다"는 전제에 의존한다. 이 전제가 깨지면
     * (예: 실수로 `GRANT SELECT`만 부여) ×4 산식이 조용히 틀려진다 — 아래 값을
     * [MigrationGrantParityTest]에서 단언해 전제 자체를 가드한다(재리뷰 p3-3 후속).
     */
    fun tableGrantPrivileges(): List<Pair<String, Set<String>>> =
        grantPrivilegeRegex.findAll(grantsSqlFile().readText())
            .map { match ->
                match.groupValues[2] to match.groupValues[1].split(",").map { it.trim().uppercase() }.toSet()
            }
            .toList()

    /**
     * 유저별 기대 테이블 권한 개수 — 02-grants.sql 을 단일 소스로 파생한다(리뷰 p3 후속).
     * 테이블 단위 GRANT 라인 수(스키마 단위 `ON sports.*` 는 제외 — grantTableRegex와 동일 기준)
     * × 4(SELECT/INSERT/UPDATE/DELETE, [tableGrantPrivileges]가 이 전제를 별도로 단언한다).
     * scripts/apply-grants.sh의 expected_count_for_user()와 **동일한 카운팅 기준**(테이블 단위
     * GRANT만, 스키마 단위 GRANT 제외)을 쓴다(재리뷰 p3-3 후속 — 이전에는 Bash 쪽이 "TO
     * '<user>'@'%';"로 끝나는 라인 수만 셌는데, 이는 GRANT 대상이 테이블 단위인지 스키마
     * 단위인지 구분하지 않아 두 산식이 실제로 달랐다).
     */
    fun expectedTablePrivilegeCount(username: String): Int =
        grantTableRegex.findAll(grantsSqlFile().readText())
            .count { it.groupValues[2] == username } * 4
}

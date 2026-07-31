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

    /**
     * 02-grants.sql 이 테이블 단위로 GRANT하는 대상 (BATCH_* 제외, flyway_migrator의
     * `ON sports.*` 스키마 단위 GRANT도 이 정규식에 매칭되지 않아 자연히 제외된다).
     */
    fun tablesGrantedInGrantsSql(): Set<String> =
        grantTableRegex.findAll(grantsSqlFile().readText())
            .map { it.groupValues[1] }
            .filterNot { it.startsWith("BATCH_") }
            .toSet()

    /**
     * 유저별 기대 테이블 권한 개수 — 02-grants.sql 을 단일 소스로 파생한다(리뷰 p3 후속).
     * "TO '<user>'@'%'" 로 끝나는 GRANT 라인 수 × 4(SELECT/INSERT/UPDATE/DELETE, 이 파일의
     * 모든 도메인 GRANT 라인이 동일한 4권한을 부여한다). scripts/apply-grants.sh의
     * expected_count_for_user()와 동일한 산식이다.
     */
    fun expectedTablePrivilegeCount(username: String): Int =
        grantTableRegex.findAll(grantsSqlFile().readText())
            .count { it.groupValues[2] == username } * 4
}

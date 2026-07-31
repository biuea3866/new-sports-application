package com.sportsapp.grant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.maps.shouldBeEmpty as shouldBeEmptyMap

/**
 * [W1-04][재리뷰 p2① 후속] Flyway 마이그레이션(실제 원본)과 02-grants.sql(실제 원본)의 테이블
 * 집합을 파일 레벨에서 직접 비교한다. 컨테이너를 띄우지 않아 빠르다.
 *
 * [DbGrantPermissionTest]의 "GRANT 스크립트가 다루는 모든 테이블에 소유 서비스 유저가 있다"
 * 테스트는 대조 대상(information_schema.tables)이 그 스펙 자신의 하드코딩 배열(domainTables)로
 * 만든 스텁 스키마였다 — 마이그레이션에 46번째 테이블이 추가돼도 그 배열에 손으로 추가하지
 * 않는 한 통과해버리는 거짓 방어였다(구 주석의 "스키마 자체를 근거로 삼으므로 즉시 실패한다"는
 * 서술은 사실과 달랐다 — 정정함). 이 테스트가 그 구멍을 메운다: 실제 마이그레이션 SQL 파일을
 * 파싱한 결과와 02-grants.sql을 양방향으로 직접 대조한다.
 */
class MigrationGrantParityTest : FunSpec({

    test("마이그레이션이 생성하는 모든 테이블이 02-grants.sql 에서 GRANT 대상이다 (BATCH_* 제외)") {
        val migrationOnly = GrantSchemaFiles.tablesCreatedByMigrations() - GrantSchemaFiles.tablesGrantedInGrantsSql()
        migrationOnly.shouldBeEmpty()
    }

    test("02-grants.sql 이 GRANT하는 모든 테이블이 마이그레이션에서 생성된다 (BATCH_* 제외)") {
        val grantsOnly = GrantSchemaFiles.tablesGrantedInGrantsSql() - GrantSchemaFiles.tablesCreatedByMigrations()
        grantsOnly.shouldBeEmpty()
    }

    // [재리뷰 p3-1] 위 두 parity 테스트는 **집합(Set) 기준**이라 "한 테이블에 두 유저가 GRANT됐는가"
    // (§2-1 "한 테이블 = 한 서비스" 원칙 위반)를 잡지 못한다 — 어느 테이블에 두 번째 유저 GRANT를
    // 추가해도 테이블 집합 자체는 그대로라 두 parity 테스트, "소유자 없음" 테스트, 자기 자신에서
    // 파생되는 권한 개수 기대값 테스트 전부 통과한다. 이 테스트가 유일하게 "테이블당 소유 유저 수"를
    // 직접 검증한다.
    test("02-grants.sql 에서 테이블마다 소유 서비스 유저가 정확히 1명이다 (단일 소유 원칙, §2-1)") {
        val multiOwnerTables = GrantSchemaFiles.tableOwnersInGrantsSql().filterValues { owners -> owners.size > 1 }
        multiOwnerTables.shouldBeEmptyMap()
    }

    // [재리뷰 p3-3] expectedTablePrivilegeCount()·apply-grants.sh의 expected_count_for_user() 는
    // 둘 다 "GRANT 라인 수 × 4"로 계산한다 — "이 파일의 모든 테이블 단위 GRANT가 예외 없이
    // SELECT/INSERT/UPDATE/DELETE 4권한을 부여한다"는 전제를 이 테스트가 직접 가드한다. 전제가
    // 깨지면(예: `GRANT SELECT`만 부여한 라인 혼입) ×4 산식이 조용히 틀려지는데, 그 오류는 두
    // 산식(Bash/Kotlin) 모두에 동일하게 반영돼 있어 서로 대조해서는 드러나지 않는다.
    test("02-grants.sql 의 모든 테이블 단위 GRANT는 SELECT/INSERT/UPDATE/DELETE 4권한이다 (×4 산식 전제 가드)") {
        val expectedPrivileges = setOf("SELECT", "INSERT", "UPDATE", "DELETE")
        val nonStandardGrants = GrantSchemaFiles.tableGrantPrivileges()
            .filter { (_, privileges) -> privileges != expectedPrivileges }
        nonStandardGrants.shouldBeEmpty()
    }
})

package com.sportsapp.grant

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty

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
})

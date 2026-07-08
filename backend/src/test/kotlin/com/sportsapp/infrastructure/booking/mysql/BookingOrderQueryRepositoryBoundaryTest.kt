package com.sportsapp.infrastructure.booking.mysql

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.kotest.core.spec.style.FunSpec

/**
 * BE-10 — booking 주문 통합조회 읽기가 facility 컨텍스트를 역참조하지 않는지 확인한다.
 * 근거: TDD "Booking 한계 명시" — Slot.facilityId(불투명 String)만 보유, facility 도메인 참조 금지.
 */
class BookingOrderQueryRepositoryBoundaryTest : FunSpec({

    val importedClasses = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.sportsapp.domain.booking", "com.sportsapp.infrastructure.booking.mysql")

    test("BookingOrderItem·BookingOrderQueryRepository·BookingOrderQueryRepositoryImpl이 facility 패키지를 참조하지 않는다") {
        noClasses()
            .that().haveSimpleNameStartingWith("BookingOrder")
            .should().dependOnClassesThat().resideInAPackage("com.sportsapp..facility..")
            .because("Booking title 라벨은 자기 컨텍스트(Slot.date/timeRange)로만 구성한다 — facility 역참조 금지")
            .check(importedClasses)
    }
})

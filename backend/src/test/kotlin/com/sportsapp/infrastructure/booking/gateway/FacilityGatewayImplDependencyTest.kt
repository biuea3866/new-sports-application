package com.sportsapp.infrastructure.booking.gateway

import com.sportsapp.domain.facility.repository.FacilityRepository
import com.sportsapp.domain.facility.service.FacilityDomainService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import kotlin.reflect.full.primaryConstructor

/**
 * PH0-01: booking → facility 게이트웨이 구현체가 FacilityRepository(=남의 테이블)를
 * 더 이상 직접 주입받지 않고, 공급자의 공개 계약인 FacilityDomainService만 의존하는지 검증한다.
 */
class FacilityGatewayImplDependencyTest : FunSpec({

    test("FacilityOwnershipGatewayImpl은 FacilityRepository 타입을 주입받지 않는다") {
        val parameterTypes = requireNotNull(FacilityOwnershipGatewayImpl::class.primaryConstructor) {
            "FacilityOwnershipGatewayImpl must have a primary constructor"
        }.parameters.map { it.type.classifier }

        parameterTypes.shouldNotContain(FacilityRepository::class)
        parameterTypes.shouldContain(FacilityDomainService::class)
    }

    test("FacilityScheduleGatewayImpl은 FacilityRepository 타입을 주입받지 않는다") {
        val parameterTypes = requireNotNull(FacilityScheduleGatewayImpl::class.primaryConstructor) {
            "FacilityScheduleGatewayImpl must have a primary constructor"
        }.parameters.map { it.type.classifier }

        parameterTypes.shouldNotContain(FacilityRepository::class)
        parameterTypes.shouldContain(FacilityDomainService::class)
    }
})

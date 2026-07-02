package com.sportsapp.architecture

import com.sportsapp.application.goods.dto.CreateMyProductCommand
import com.sportsapp.application.goods.usecase.CreateMyProductUseCase
import com.sportsapp.application.ticketing.dto.CreateMyEventCommand
import com.sportsapp.application.ticketing.dto.CreateMyEventResult
import com.sportsapp.application.ticketing.usecase.CreateMyEventUseCase
import com.sportsapp.domain.common.security.OwnershipGuard
import com.sportsapp.domain.goods.dto.ProductWithStock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * [goods·ticketing 제공 UseCase 경계 계약 동결 (FR-4)]
 * 근거: TDD "FR-4 결정", "인터페이스 시그니처 — 동결 대상" / ADR-003.
 *
 * ② B2B 파트너 연동 과제가 코드 변경 없이 경유하는 두 UseCase 의 시그니처를 리플렉션으로 동결한다.
 * 시그니처가 리팩터링으로 바뀌면 이 테스트가 RED 로 조기 감지한다. 프로덕션 코드 무변경.
 */
class ProvidedInterfaceContractTest : FunSpec({

    test("CreateMyProductUseCase.execute 는 CreateMyProductCommand 를 받아 ProductWithStock 을 반환한다") {
        val executeFunction = CreateMyProductUseCase::class.memberFunctions.single { it.name == "execute" }
        val parameterTypesExcludingReceiver = executeFunction.parameters.drop(1).map { it.type.classifier }

        parameterTypesExcludingReceiver shouldBe listOf(CreateMyProductCommand::class)
        executeFunction.returnType.classifier shouldBe ProductWithStock::class
    }

    test("CreateMyEventUseCase.execute 는 CreateMyEventCommand 를 받아 CreateMyEventResult 를 반환한다") {
        val executeFunction = CreateMyEventUseCase::class.memberFunctions.single { it.name == "execute" }
        val parameterTypesExcludingReceiver = executeFunction.parameters.drop(1).map { it.type.classifier }

        parameterTypesExcludingReceiver shouldBe listOf(CreateMyEventCommand::class)
        executeFunction.returnType.classifier shouldBe CreateMyEventResult::class
    }

    test("CreateMyEventCommand 에 ownerUserId: Long 필드가 존재한다 (파트너 Controller가 SecurityContext id를 채우는 접점)") {
        val ownerUserIdProperty = CreateMyEventCommand::class.memberProperties.single { it.name == "ownerUserId" }

        ownerUserIdProperty.returnType.classifier shouldBe Long::class
    }

    test("CreateMyProductUseCase가 OwnershipGuard에 의존한다 (SecurityContext 소유자 해석 경로 보증)") {
        val constructorParameterTypes = CreateMyProductUseCase::class.primaryConstructor
            ?.parameters
            ?.map { it.type.classifier }
            .orEmpty()

        constructorParameterTypes.shouldContain(OwnershipGuard::class)
    }

    test("(위반 시나리오 참고) execute 반환 타입이 바뀌면 계약 테스트가 실패해 ② 경유 파손을 조기 감지한다") {
        // 위 두 execute 시그니처 테스트 자체가 회귀 감시다 — 반환 타입이 CreateMyEventResult가 아니게 되는 순간
        // 즉시 실패한다. 이 테스트는 그 감지 지점이 실제로 CreateMyEventResult 클래스 동일성 비교임을 명시한다.
        val executeFunction = CreateMyEventUseCase::class.memberFunctions.single { it.name == "execute" }
        (executeFunction.returnType.classifier == CreateMyEventResult::class).shouldBeTrue()
    }
})

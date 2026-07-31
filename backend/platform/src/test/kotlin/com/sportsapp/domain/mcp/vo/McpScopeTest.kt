package com.sportsapp.domain.mcp.vo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class McpScopeTest : BehaviorSpec({

    Given("read:facility 형식의 scope 문자열") {
        val scope = McpScope.of("read:facility")

        Then("[U-08] verb=read, domain=facility, qualifier=null로 파싱된다") {
            scope.verb shouldBe "read"
            scope.domain shouldBe "facility"
            scope.qualifier.shouldBeNull()
        }

        Then("[U-09] permission name이 mcp.facility.read.own으로 변환된다") {
            scope.toPermissionName() shouldBe "mcp.facility.read.own"
        }
    }

    Given("write:booking:any 형식의 scope 문자열") {
        val scope = McpScope.of("write:booking:any")

        Then("[U-08] verb=write, domain=booking, qualifier=any로 파싱된다") {
            scope.verb shouldBe "write"
            scope.domain shouldBe "booking"
            scope.qualifier shouldBe "any"
        }

        Then("[U-09] permission name이 mcp.booking.write.any로 변환된다") {
            scope.toPermissionName() shouldBe "mcp.booking.write.any"
        }
    }

    Given("잘못된 형식의 scope 문자열") {
        Then("[U-08] 파싱 시 IllegalArgumentException이 발생한다") {
            shouldThrow<IllegalArgumentException> {
                McpScope.of("invalid-format")
            }
        }
    }

    Given("qualifier가 없는 McpScope") {
        val scope = McpScope(verb = "read", domain = "facility", qualifier = null)

        Then("asString()은 verb:domain 형식으로 직렬화한다") {
            scope.asString() shouldBe "read:facility"
        }
    }

    Given("qualifier가 있는 McpScope") {
        val scope = McpScope(verb = "write", domain = "booking", qualifier = "any")

        Then("asString()은 verb:domain:qualifier 형식으로 직렬화한다") {
            scope.asString() shouldBe "write:booking:any"
        }
    }

    Given("mcp.facility.read.own 형식의 permission name") {
        Then("fromPermissionName은 qualifier=null(own 생략)인 McpScope를 반환한다") {
            val scope = McpScope.fromPermissionName("mcp.facility.read.own")
            scope.shouldNotBeNull()
            scope.verb shouldBe "read"
            scope.domain shouldBe "facility"
            scope.qualifier.shouldBeNull()
        }
    }

    Given("mcp.booking.write.any 형식의 permission name") {
        Then("fromPermissionName은 qualifier=any인 McpScope를 반환한다") {
            val scope = McpScope.fromPermissionName("mcp.booking.write.any")
            scope.shouldNotBeNull()
            scope.qualifier shouldBe "any"
        }
    }

    Given("mcp. 로 시작하지 않는 permission name") {
        Then("fromPermissionName은 null을 반환한다") {
            McpScope.fromPermissionName("other.facility.read.own").shouldBeNull()
        }
    }

    Given("부분이 4개 미만인 permission name") {
        Then("fromPermissionName은 null을 반환한다") {
            McpScope.fromPermissionName("mcp.facility.read").shouldBeNull()
        }
    }
})

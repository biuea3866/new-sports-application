package com.sportsapp.domain.mcp.vo

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldBeNull

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
})

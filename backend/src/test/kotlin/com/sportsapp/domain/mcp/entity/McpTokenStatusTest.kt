package com.sportsapp.domain.mcp.entity

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class McpTokenStatusTest : BehaviorSpec({

    Given("McpTokenStatus 전이 규칙") {
        Then("[U-07] ACTIVE→SUSPENDED는 허용된다") {
            McpTokenStatus.ACTIVE.canTransitTo(McpTokenStatus.SUSPENDED) shouldBe true
        }

        Then("[U-07] ACTIVE→REVOKED는 허용된다") {
            McpTokenStatus.ACTIVE.canTransitTo(McpTokenStatus.REVOKED) shouldBe true
        }

        Then("[U-07] SUSPENDED→ACTIVE는 허용된다") {
            McpTokenStatus.SUSPENDED.canTransitTo(McpTokenStatus.ACTIVE) shouldBe true
        }

        Then("[U-07] REVOKED→ACTIVE는 허용되지 않는다") {
            McpTokenStatus.REVOKED.canTransitTo(McpTokenStatus.ACTIVE) shouldBe false
        }

        Then("[U-07] REVOKED→SUSPENDED는 허용되지 않는다") {
            McpTokenStatus.REVOKED.canTransitTo(McpTokenStatus.SUSPENDED) shouldBe false
        }

        Then("[U-07] SUSPENDED→REVOKED는 허용된다") {
            McpTokenStatus.SUSPENDED.canTransitTo(McpTokenStatus.REVOKED) shouldBe true
        }
    }
})

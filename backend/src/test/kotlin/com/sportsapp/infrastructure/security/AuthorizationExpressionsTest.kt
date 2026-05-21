package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.UserPrincipal
import com.sportsapp.presentation.mcp.security.McpAuthenticatedPrincipal
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class AuthorizationExpressionsTest : BehaviorSpec({

    val authorizationExpressions = AuthorizationExpressions()

    afterEach {
        SecurityContextHolder.clearContext()
    }

    fun setAuthentication(userId: Long, vararg roles: String) {
        val principal = UserPrincipal(id = userId, email = "test@example.com", roles = roles.toList())
        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, authorities)
    }

    Given("SecurityContext principal.id 가 1L인 사용자") {
        When("[U-01] isOwner(1L) 호출 시") {
            setAuthentication(1L, "USER")
            Then("principal.id 와 인자가 일치하므로 true 를 반환한다") {
                authorizationExpressions.isOwner(1L) shouldBe true
            }
        }

        When("[U-01] isOwner(2L) 호출 시") {
            setAuthentication(1L, "USER")
            Then("principal.id 와 인자가 불일치하므로 false 를 반환한다") {
                authorizationExpressions.isOwner(2L) shouldBe false
            }
        }
    }

    Given("SecurityContext 에 인증 정보가 없는 상태") {
        When("[U-01] isOwner(1L) 호출 시") {
            SecurityContextHolder.clearContext()
            Then("principal 이 없으므로 false 를 반환한다") {
                authorizationExpressions.isOwner(1L) shouldBe false
            }
        }
    }

    Given("FACILITY_OWNER 롤을 가진 사용자 id=5L") {
        When("[U-02] isFacilityOwner(5L) 호출 시") {
            setAuthentication(5L, "FACILITY_OWNER")
            Then("FACILITY_OWNER 롤 보유 + 본인이므로 true 를 반환한다") {
                authorizationExpressions.isFacilityOwner(5L) shouldBe true
            }
        }

        When("[U-02] isFacilityOwner(6L) 호출 시 — 타인 userId") {
            setAuthentication(5L, "FACILITY_OWNER")
            Then("FACILITY_OWNER 롤 보유이나 본인이 아니므로 false 를 반환한다") {
                authorizationExpressions.isFacilityOwner(6L) shouldBe false
            }
        }
    }

    Given("USER 롤만 가진 사용자 id=7L") {
        When("[U-02] isFacilityOwner(7L) 호출 시") {
            setAuthentication(7L, "USER")
            Then("FACILITY_OWNER 롤이 없으므로 false 를 반환한다") {
                authorizationExpressions.isFacilityOwner(7L) shouldBe false
            }
        }
    }

    Given("MCP principal 이 BOOKING_READ scope 를 가진 상태") {
        val mcpPrincipal = object : McpAuthenticatedPrincipal {
            override val tokenId: Long = 10L
            override val userId: Long = 1L
            override val grantedScopes: Set<String> = setOf("BOOKING_READ", "FACILITY_READ")
        }

        beforeEach {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(mcpPrincipal, null, emptyList())
        }

        When("[U-03] hasMcpScope(\"BOOKING_READ\") 호출 시") {
            Then("grantedScopes 에 포함되므로 true 를 반환한다") {
                authorizationExpressions.hasMcpScope("BOOKING_READ") shouldBe true
            }
        }

        When("[U-04] hasMcpScope(\"PAYMENT_WRITE\") 호출 시") {
            Then("grantedScopes 에 없으므로 false 를 반환한다") {
                authorizationExpressions.hasMcpScope("PAYMENT_WRITE") shouldBe false
            }
        }
    }

    Given("principal 이 일반 UserPrincipal 인 상태") {
        When("[U-05] hasMcpScope(\"BOOKING_READ\") 호출 시") {
            setAuthentication(1L, "USER")
            Then("McpAuthenticatedPrincipal 이 아니므로 false 를 반환한다") {
                authorizationExpressions.hasMcpScope("BOOKING_READ") shouldBe false
            }
        }
    }

    Given("SecurityContext 에 인증 정보가 없는 상태") {
        When("[U-06] hasMcpScope(\"BOOKING_READ\") 호출 시") {
            SecurityContextHolder.clearContext()
            Then("authentication 이 null 이므로 false 를 반환한다") {
                authorizationExpressions.hasMcpScope("BOOKING_READ") shouldBe false
            }
        }
    }
})

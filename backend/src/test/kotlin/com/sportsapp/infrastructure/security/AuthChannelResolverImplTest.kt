package com.sportsapp.infrastructure.security

import com.sportsapp.domain.user.vo.UserPrincipal
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class AuthChannelResolverImplTest : BehaviorSpec({

    val authChannelResolver = AuthChannelResolverImpl()

    afterEach {
        SecurityContextHolder.clearContext()
    }

    fun setAuthentication(principal: UserPrincipal) {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    Given("SecurityContext principal 이 partnerAuthenticated=true 인 경우") {
        setAuthentication(
            UserPrincipal(id = 10L, email = "partner-10@example.com", roles = listOf("GOODS_SELLER"), partnerAuthenticated = true),
        )

        When("isPartnerAuthenticated() 를 호출하면") {
            Then("true 를 반환한다") {
                authChannelResolver.isPartnerAuthenticated() shouldBe true
            }
        }
    }

    Given("SecurityContext principal 이 JWT 인증(partnerAuthenticated=false) 인 경우") {
        setAuthentication(
            UserPrincipal(id = 1L, email = "user@example.com", roles = listOf("USER")),
        )

        When("isPartnerAuthenticated() 를 호출하면") {
            Then("false 를 반환한다") {
                authChannelResolver.isPartnerAuthenticated() shouldBe false
            }
        }
    }

    Given("SecurityContext 에 인증 정보가 없는 경우 (미인증)") {
        SecurityContextHolder.clearContext()

        When("isPartnerAuthenticated() 를 호출하면") {
            Then("false 를 반환한다") {
                authChannelResolver.isPartnerAuthenticated() shouldBe false
            }
        }
    }
})

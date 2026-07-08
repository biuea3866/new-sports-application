package com.sportsapp.infrastructure.audit

import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.infrastructure.persistence.audit.MongoAuditorAware
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.optional.shouldBeEmpty
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl

/**
 * U-01, U-02: MongoAuditorAware.getCurrentAuditor() 단위 테스트
 */
class MongoAuditorAwareTest : BehaviorSpec({

    val auditorAware = MongoAuditorAware()

    afterEach {
        SecurityContextHolder.clearContext()
    }

    Given("인증된 사용자가 SecurityContext에 설정된 상태") {
        val userId = 42L
        val principal = UserPrincipal(id = userId, email = "user@test.com", roles = listOf("ROLE_USER"))
        val authentication = UsernamePasswordAuthenticationToken(principal, null, emptyList())
        val context = SecurityContextImpl(authentication)
        SecurityContextHolder.setContext(context)

        When("getCurrentAuditor()를 호출하면") {
            val result = auditorAware.getCurrentAuditor()

            Then("[U-01] authUserId를 Optional<Long>으로 반환한다") {
                result.shouldBePresent()
                result.get() shouldBe userId
            }
        }
    }

    Given("인증 컨텍스트가 없는 상태") {
        SecurityContextHolder.clearContext()

        When("getCurrentAuditor()를 호출하면") {
            val result = auditorAware.getCurrentAuditor()

            Then("[U-02] Optional.empty()를 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }

    Given("인증되지 않은(isAuthenticated=false) authentication이 설정된 상태") {
        val authentication = UsernamePasswordAuthenticationToken(null, null)
        val context = SecurityContextImpl(authentication)
        SecurityContextHolder.setContext(context)

        When("getCurrentAuditor()를 호출하면") {
            val result = auditorAware.getCurrentAuditor()

            Then("[U-02] Optional.empty()를 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }
})

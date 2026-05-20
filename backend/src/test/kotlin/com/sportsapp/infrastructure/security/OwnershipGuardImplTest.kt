package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.user.UserPrincipal
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

class OwnershipGuardImplTest : BehaviorSpec({

    val ownershipGuard = OwnershipGuardImpl()

    afterEach {
        SecurityContextHolder.clearContext()
    }

    fun setAuthentication(userId: Long) {
        val principal = UserPrincipal(id = userId, email = "test@example.com", roles = listOf("USER"))
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }

    Given("ownerUserId 와 authUserId 가 동일한 경우") {
        When("[U-01] requireOwned 호출 시") {
            Then("예외 없이 정상 통과한다") {
                shouldNotThrowAny {
                    ownershipGuard.requireOwned(ownerUserId = 1L, authUserId = 1L)
                }
            }
        }
    }

    Given("ownerUserId 와 authUserId 가 다른 경우") {
        When("[U-02] requireOwned 호출 시") {
            Then("ResourceNotFoundException(404) 이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    ownershipGuard.requireOwned(ownerUserId = 1L, authUserId = 2L)
                }
            }
        }
    }

    Given("ownerUserId 가 null 인 경우 (B2C 호환 리소스)") {
        When("[U-03] requireOwned 호출 시") {
            Then("B2C 호환 정책에 따라 예외 없이 통과한다") {
                shouldNotThrowAny {
                    ownershipGuard.requireOwned(ownerUserId = null, authUserId = 1L)
                }
            }
        }
    }

    Given("SecurityContext 에 인증된 사용자 id=10L 이 있는 경우") {
        When("authUserId() 호출 시") {
            setAuthentication(10L)
            Then("[U-04] SecurityContext 에서 10L 을 반환한다") {
                ownershipGuard.authUserId() shouldBe 10L
            }
        }
    }

    Given("SecurityContext 에 인증 정보가 없는 경우") {
        When("authUserId() 호출 시") {
            SecurityContextHolder.clearContext()
            Then("[U-05] UnauthorizedException(401) 이 발생한다") {
                shouldThrow<UnauthorizedException> {
                    ownershipGuard.authUserId()
                }
            }
        }
    }
})

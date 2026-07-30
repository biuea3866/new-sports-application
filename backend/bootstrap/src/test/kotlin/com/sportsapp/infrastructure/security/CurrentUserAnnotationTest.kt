package com.sportsapp.infrastructure.security

import com.sportsapp.presentation.security.CurrentUser
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.security.core.annotation.AuthenticationPrincipal

class CurrentUserAnnotationTest : BehaviorSpec({

    Given("@CurrentUser 어노테이션") {
        When("[U-03] 어노테이션에 @AuthenticationPrincipal 이 부착되어 있는지 확인하면") {
            val isAnnotationPresent = CurrentUser::class.java.isAnnotationPresent(AuthenticationPrincipal::class.java)

            Then("@AuthenticationPrincipal 이 존재하여 Spring MVC ArgumentResolver 가 UserPrincipal 을 주입한다") {
                isAnnotationPresent shouldBe true
            }
        }
    }
})

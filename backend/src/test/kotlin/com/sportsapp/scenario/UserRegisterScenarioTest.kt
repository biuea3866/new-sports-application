package com.sportsapp.scenario

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.user.UserDomainService
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class UserRegisterScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
) : BaseIntegrationTest() {

    init {
        Given("신규 가입 요청") {
            When("UserDomainService.register 를 호출하면") {
                Then("[S-01] 기본 USER Role 이 자동 부여되어 findByIdWithRoles 조회 시 Role 1건이 포함된다") {
                    val user = userDomainService.register("scenario@example.com", "hashedPassword")
                    val loaded = userDomainService.findByIdWithRoles(user.id)
                    loaded.getRoles().size shouldBe 1
                    loaded.getRoles().first().name shouldBe "USER"
                }
            }
        }
    }
}

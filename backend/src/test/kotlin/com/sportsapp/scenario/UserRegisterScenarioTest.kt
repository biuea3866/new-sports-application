package com.sportsapp.scenario

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.presentation.user.dto.response.RegisterUserResponse
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus

class UserRegisterScenarioTest(
    @Autowired private val restTemplate: TestRestTemplate,
) : BaseIntegrationTest() {

    init {
        Given("신규 가입 요청") {
            When("POST /users/register 를 호출하면") {
                Then("[S-01] 201 Created + Location 헤더가 반환되고 password_hash 는 노출되지 않는다") {
                    val requestBody = mapOf("email" to "scenario@example.com", "password" to "password1234")
                    val response = restTemplate.postForEntity("/users/register", requestBody, RegisterUserResponse::class.java)

                    response.statusCode shouldBe HttpStatus.CREATED
                    response.headers["Location"].shouldNotBeNull()
                    val body = response.body
                    body.shouldNotBeNull()
                    body.email shouldBe "scenario@example.com"
                }
            }
        }

        Given("이미 가입된 이메일로 재가입 시도") {
            restTemplate.postForEntity(
                "/users/register",
                mapOf("email" to "dup-scenario@example.com", "password" to "password1234"),
                RegisterUserResponse::class.java,
            )

            When("동일 이메일로 POST /users/register 를 다시 호출하면") {
                Then("[S-02] 409 ProblemDetail 응답이 반환된다") {
                    val response = restTemplate.postForEntity(
                        "/users/register",
                        mapOf("email" to "dup-scenario@example.com", "password" to "password1234"),
                        String::class.java,
                    )
                    response.statusCode shouldBe HttpStatus.CONFLICT
                }
            }
        }

        Given("잘못된 이메일 형식 입력") {
            When("POST /users/register 를 호출하면") {
                Then("[S-03] 400 응답이 반환된다") {
                    val response = restTemplate.postForEntity(
                        "/users/register",
                        mapOf("email" to "not-an-email", "password" to "password1234"),
                        String::class.java,
                    )
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }
}

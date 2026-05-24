package com.sportsapp.presentation.ticketing

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container

/**
 * [E2E-05-E05] DEF-005: POST /events/{id}/seats/select 빈 seatIds 입력 시 400/422 를 반환해야 한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class EventApiControllerTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    companion object {
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("sports")
            .withUsername("test")
            .withPassword("test")
            .also { it.start() }
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    init {
        Given("좌석 선택 API 에 빈 seatIds 를 전송할 때") {
            When("[E2E-05-E05] POST /events/1/seats/select body={seatIds: []} 요청 시") {
                Then("500 이 아닌 422 Unprocessable Entity 를 반환한다") {
                    mockMvc.post("/events/1/seats/select") {
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"seatIds": []}"""
                        header("X-User-Id", "7")
                    }.andExpect {
                        status { isUnprocessableEntity() }
                    }
                }
            }
        }
    }
}

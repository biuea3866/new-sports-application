package com.sportsapp.health

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.containers.MySQLContainer
import com.sportsapp.SportsTestContainers

/**
 * S-01: SpringBootTest 컨텍스트 로드 후 GET /actuator/health 가 200 + {"status":"UP"} 을 반환한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class HealthEndpointIntegrationTest {

    companion object {
        @JvmStatic
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = SportsTestContainers.mysql
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun `GET actuator health 는 200 OK 와 status UP 을 반환한다`() {
        mockMvc.perform(
            get("/actuator/health")
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("UP"))
    }
}

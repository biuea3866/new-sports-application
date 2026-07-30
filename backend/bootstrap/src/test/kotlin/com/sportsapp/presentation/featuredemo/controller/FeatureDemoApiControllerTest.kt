package com.sportsapp.presentation.featuredemo.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.sportsapp.application.featuredemo.dto.DemoGreetingResponse
import com.sportsapp.application.featuredemo.dto.GetDemoGreetingCommand
import com.sportsapp.application.featuredemo.usecase.GetDemoGreetingUseCase
import com.sportsapp.domain.featuredemo.exception.FeatureDisabledException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.MediaType
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.ZonedDateTime

/**
 * SecurityConfig(`feature-demo` 하위 경로 permitAll 배선)는 BE-10 담당 — 전역 시큐리티 체인에 의존하지 않는
 * standalone MockMvc 슬라이스로 컨트롤러·GlobalExceptionHandler 연동만 검증한다.
 */
class FeatureDemoApiControllerTest : BehaviorSpec({

    val getDemoGreetingUseCase = mockk<GetDemoGreetingUseCase>()
    val objectMapper = ObjectMapper().registerModule(JavaTimeModule())
    val mockMvc = MockMvcBuilders
        .standaloneSetup(FeatureDemoApiController(getDemoGreetingUseCase))
        .setControllerAdvice(GlobalExceptionHandler())
        .setMessageConverters(MappingJackson2HttpMessageConverter(objectMapper))
        .build()

    Given("demo.feature.hello 플래그가 ON이고 X-User-Id 헤더가 있는 상태") {
        every {
            getDemoGreetingUseCase.execute(GetDemoGreetingCommand(userId = 7L))
        } returns DemoGreetingResponse(
            message = "Hello from the feature-flagged demo endpoint",
            flagKey = "demo.feature.hello",
            userId = 7L,
            servedAt = ZonedDateTime.now(),
        )

        When("GET /feature-demo/hello 요청 시") {
            Then("200과 인사 메시지 본문을 반환한다") {
                mockMvc.perform(
                    get("/feature-demo/hello")
                        .header("X-User-Id", 7L)
                        .accept(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.flagKey").value("demo.feature.hello"))
                    .andExpect(jsonPath("$.userId").value(7))
                    .andExpect(jsonPath("$.message").exists())
                    .andExpect(jsonPath("$.servedAt").exists())
            }
        }
    }

    Given("플래그가 비활성 판정(미존재·OFF·archive·롤아웃 미포함)인 상태") {
        every {
            getDemoGreetingUseCase.execute(GetDemoGreetingCommand(userId = 8L))
        } throws FeatureDisabledException("demo.feature.hello")

        When("GET /feature-demo/hello 요청 시") {
            Then("503 ProblemDetail을 반환한다") {
                mockMvc.perform(
                    get("/feature-demo/hello")
                        .header("X-User-Id", 8L)
                        .accept(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isServiceUnavailable)
                    .andExpect(jsonPath("$.properties.code").value("FEATURE_DISABLED"))
            }
        }
    }

    Given("X-User-Id 헤더 없이 요청하는 상태") {
        every {
            getDemoGreetingUseCase.execute(GetDemoGreetingCommand(userId = null))
        } throws FeatureDisabledException("demo.feature.hello")

        When("GET /feature-demo/hello 요청 시") {
            Then("userId 없이 UseCase가 호출되고 default(false)로 503을 반환한다") {
                mockMvc.perform(
                    get("/feature-demo/hello")
                        .accept(MediaType.APPLICATION_JSON)
                )
                    .andExpect(status().isServiceUnavailable)
                    .andExpect(jsonPath("$.properties.code").value("FEATURE_DISABLED"))
            }
        }
    }
})

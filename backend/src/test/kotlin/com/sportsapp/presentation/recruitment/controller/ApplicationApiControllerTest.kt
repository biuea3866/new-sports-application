package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.CancelApplicationCommand
import com.sportsapp.application.recruitment.usecase.CancelApplicationUseCase
import com.sportsapp.application.recruitment.usecase.ListMyApplicationsUseCase
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.exception.ApplicationCancellationClosedException
import com.sportsapp.domain.recruitment.exception.UnauthorizedApplicationAccessException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

class ApplicationApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        listMyApplicationsUseCase: ListMyApplicationsUseCase = mockk(),
        cancelApplicationUseCase: CancelApplicationUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        ApplicationApiController(
            listMyApplicationsUseCase = listMyApplicationsUseCase,
            cancelApplicationUseCase = cancelApplicationUseCase,
        ),
    ).setControllerAdvice(GlobalExceptionHandler()).build()

    Given("본인 신청 목록 조회 요청") {
        val listMyApplicationsUseCase = mockk<ListMyApplicationsUseCase>()
        every { listMyApplicationsUseCase.execute(TEST_USER_ID) } returns listOf(
            ApplicationResponse(
                id = 11L,
                recruitmentId = 1L,
                status = ApplicationStatus.CONFIRMED,
                paymentId = 99L,
                appliedAt = ZonedDateTime.now(),
            ),
        )
        val mockMvc = buildMockMvc(listMyApplicationsUseCase = listMyApplicationsUseCase)

        When("GET /applications 요청 시") {
            val result = mockMvc.perform(get("/applications").header("X-User-Id", TEST_USER_ID))

            Then("200과 함께 본인 신청 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(11))
            }
        }
    }

    Given("신청 이력이 없는 사용자의 조회") {
        val listMyApplicationsUseCase = mockk<ListMyApplicationsUseCase>()
        every { listMyApplicationsUseCase.execute(200L) } returns emptyList()
        val mockMvc = buildMockMvc(listMyApplicationsUseCase = listMyApplicationsUseCase)

        When("GET /applications 요청 시") {
            val result = mockMvc.perform(get("/applications").header("X-User-Id", 200L))

            Then("200과 함께 빈 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }

    Given("신청자 본인의 취소 요청") {
        val cancelApplicationUseCase = mockk<CancelApplicationUseCase>()
        every {
            cancelApplicationUseCase.execute(CancelApplicationCommand(applicationId = 11L, applicantUserId = TEST_USER_ID))
        } returns ApplicationResponse(
            id = 11L,
            recruitmentId = 1L,
            status = ApplicationStatus.CANCELLED,
            paymentId = 99L,
            appliedAt = ZonedDateTime.now(),
        )
        val mockMvc = buildMockMvc(cancelApplicationUseCase = cancelApplicationUseCase)

        When("POST /applications/11/cancel 요청 시") {
            val result = mockMvc.perform(post("/applications/11/cancel").header("X-User-Id", TEST_USER_ID))

            Then("200과 함께 CANCELLED 상태를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
            }
        }
    }

    Given("본인 소유가 아닌 신청 취소 요청") {
        val cancelApplicationUseCase = mockk<CancelApplicationUseCase>()
        every { cancelApplicationUseCase.execute(any()) } throws UnauthorizedApplicationAccessException(11L)
        val mockMvc = buildMockMvc(cancelApplicationUseCase = cancelApplicationUseCase)

        When("POST /applications/11/cancel 요청 시") {
            val result = mockMvc.perform(post("/applications/11/cancel").header("X-User-Id", 999L))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("APPLICATION_ACCESS_DENIED"))
            }
        }
    }

    Given("신청 마감 이후의 취소 요청") {
        val cancelApplicationUseCase = mockk<CancelApplicationUseCase>()
        every { cancelApplicationUseCase.execute(any()) } throws ApplicationCancellationClosedException(11L)
        val mockMvc = buildMockMvc(cancelApplicationUseCase = cancelApplicationUseCase)

        When("POST /applications/11/cancel 요청 시") {
            val result = mockMvc.perform(post("/applications/11/cancel").header("X-User-Id", TEST_USER_ID))

            Then("422를 반환한다") {
                result.andExpect(status().isUnprocessableEntity)
                    .andExpect(jsonPath("$.code").value("APPLICATION_CANCELLATION_CLOSED"))
            }
        }
    }
})

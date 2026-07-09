package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.ApplicationDetailResponse
import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.CancelApplicationCommand
import com.sportsapp.application.recruitment.usecase.CancelApplicationUseCase
import com.sportsapp.application.recruitment.usecase.GetApplicationDetailUseCase
import com.sportsapp.application.recruitment.usecase.ListMyApplicationsUseCase
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.recruitment.dto.ApplicationDetail
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.exception.ApplicationCancellationClosedException
import com.sportsapp.domain.recruitment.exception.UnauthorizedApplicationAccessException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZoneOffset
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
        getApplicationDetailUseCase: GetApplicationDetailUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        ApplicationApiController(
            listMyApplicationsUseCase = listMyApplicationsUseCase,
            cancelApplicationUseCase = cancelApplicationUseCase,
            getApplicationDetailUseCase = getApplicationDetailUseCase,
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

    Given("본인 소유의 신청 상세 조회 요청") {
        val getApplicationDetailUseCase = mockk<GetApplicationDetailUseCase>()
        every {
            getApplicationDetailUseCase.execute(applicationId = 11L, requesterUserId = TEST_USER_ID)
        } returns ApplicationDetailResponse.of(
            ApplicationDetail(
                applicationId = 11L,
                recruitmentId = 1L,
                recruitmentTitle = "주말 축구 모임",
                status = ApplicationStatus.CONFIRMED,
                feeAmount = BigDecimal("10000"),
                paymentId = 701L,
                createdAt = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC),
            ),
        )
        val mockMvc = buildMockMvc(getApplicationDetailUseCase = getApplicationDetailUseCase)

        When("GET /applications/11 요청 시") {
            val result = mockMvc.perform(get("/applications/11").header("X-User-Id", TEST_USER_ID))

            Then("200과 함께 모집명·참가비를 포함한 상세를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.applicationId").value(11))
                    .andExpect(jsonPath("$.recruitmentId").value(1))
                    .andExpect(jsonPath("$.recruitmentTitle").value("주말 축구 모임"))
                    .andExpect(jsonPath("$.status").value("CONFIRMED"))
                    .andExpect(jsonPath("$.feeAmount").value(10000))
                    .andExpect(jsonPath("$.paymentId").value(701))
            }
        }
    }

    Given("본인 소유가 아닌 신청 상세 조회 요청") {
        val getApplicationDetailUseCase = mockk<GetApplicationDetailUseCase>()
        every {
            getApplicationDetailUseCase.execute(applicationId = 11L, requesterUserId = 999L)
        } throws UnauthorizedApplicationAccessException(11L)
        val mockMvc = buildMockMvc(getApplicationDetailUseCase = getApplicationDetailUseCase)

        When("GET /applications/11 요청 시") {
            val result = mockMvc.perform(get("/applications/11").header("X-User-Id", 999L))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("APPLICATION_ACCESS_DENIED"))
            }
        }
    }

    Given("존재하지 않는 신청 상세 조회 요청") {
        val getApplicationDetailUseCase = mockk<GetApplicationDetailUseCase>()
        every {
            getApplicationDetailUseCase.execute(applicationId = 404L, requesterUserId = TEST_USER_ID)
        } throws ResourceNotFoundException("Application", 404L)
        val mockMvc = buildMockMvc(getApplicationDetailUseCase = getApplicationDetailUseCase)

        When("GET /applications/404 요청 시") {
            val result = mockMvc.perform(get("/applications/404").header("X-User-Id", TEST_USER_ID))

            Then("404를 반환한다") {
                result.andExpect(status().isNotFound)
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
            }
        }
    }
})

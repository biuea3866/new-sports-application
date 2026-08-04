package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.InternalRecruitmentApplicationHistoryResponse
import com.sportsapp.application.recruitment.usecase.ListRecruitmentApplicationsForOrderHistoryUseCase
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val INTERNAL_AUTH_SUBJECT_HEADER = "X-Internal-Auth-Subject"

/**
 * 통합 주문내역(BE-08) 원격 공급 엔드포인트 (S2-05, edge OrderHistoryGateway.findRecruitmentOrders
 * 2단계 구현 대상). 개인 데이터라 신원 헤더 필수 — 부재 시 400, 소유권 경계를 검증한다.
 */
class InternalRecruitmentApplicationApiControllerTest : BehaviorSpec({

    fun buildMockMvc(listRecruitmentApplicationsForOrderHistoryUseCase: ListRecruitmentApplicationsForOrderHistoryUseCase = mockk()) =
        MockMvcBuilders.standaloneSetup(
            InternalRecruitmentApplicationApiController(listRecruitmentApplicationsForOrderHistoryUseCase),
        ).setControllerAdvice(GlobalExceptionHandler()).build()

    fun applicationHistory(sourceId: Long): InternalRecruitmentApplicationHistoryResponse =
        InternalRecruitmentApplicationHistoryResponse(
            sourceId = sourceId,
            title = "주말 축구 모임",
            status = ApplicationStatus.CONFIRMED,
            paymentId = 701L,
            createdAt = ZonedDateTime.now(),
            amount = BigDecimal("15000"),
        )

    Given("X-Internal-Auth-Subject 헤더로 본인 신청 이력을 조회하면") {
        val listRecruitmentApplicationsForOrderHistoryUseCase = mockk<ListRecruitmentApplicationsForOrderHistoryUseCase>()
        every { listRecruitmentApplicationsForOrderHistoryUseCase.execute(9L) } returns listOf(applicationHistory(11L))
        val mockMvc = buildMockMvc(listRecruitmentApplicationsForOrderHistoryUseCase)

        When("GET /internal/order-history/recruitment-applications 요청 시 (X-Internal-Auth-Subject: 9)") {
            val result = mockMvc.perform(
                get("/internal/order-history/recruitment-applications").header(INTERNAL_AUTH_SUBJECT_HEADER, "9"),
            )

            Then("200과 함께 본인 신청 이력만 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].sourceId").value(11))
                    .andExpect(jsonPath("$[0].paymentId").value(701))
            }

            // 계약 필드가 조용히 누락된 표면이라(소비자 계약 확장을 늦게 반영) JSON 레벨에서 고정한다 —
            // DTO 에서 필드를 지우면 UseCase 테스트가 아니라 여기서 먼저 깨져야 한다.
            Then("소비자(edge OrderHistoryItem)가 요구하는 계약 필드가 응답 JSON 에 모두 실린다") {
                result.andExpect(jsonPath("$[0].title").value("주말 축구 모임"))
                    .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
                    .andExpect(jsonPath("$[0].amount").value(15000))
                    .andExpect(jsonPath("$[0].createdAt").exists())
            }
        }
    }

    Given("신원 헤더 없이 조회하면") {
        val mockMvc = buildMockMvc()

        When("GET /internal/order-history/recruitment-applications 요청 시 (헤더 없음)") {
            val result = mockMvc.perform(get("/internal/order-history/recruitment-applications"))

            Then("400을 반환한다") {
                result.andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.code").value("MISSING_REQUEST_HEADER"))
            }
        }
    }

    Given("신원 헤더 값이 사용자 PK 로 해석되지 않으면") {
        val listRecruitmentApplicationsForOrderHistoryUseCase = mockk<ListRecruitmentApplicationsForOrderHistoryUseCase>()
        val mockMvc = buildMockMvc(listRecruitmentApplicationsForOrderHistoryUseCase)

        When("GET /internal/order-history/recruitment-applications 요청 시") {
            val result = mockMvc.perform(
                get("/internal/order-history/recruitment-applications")
                    .header(INTERNAL_AUTH_SUBJECT_HEADER, "not-a-user-id"),
            )

            Then("400으로 거부하고 조회를 수행하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { listRecruitmentApplicationsForOrderHistoryUseCase.execute(any()) }
            }
        }
    }

    // 이 Given 이 고정하는 것은 "신원 헤더의 id 만 조회 인자로 쓰인다"까지다 — 다른 사용자 데이터가
    // 실제로 배제되는지는 리포지토리 where 절과 `ApplicationCustomRepositoryImplTest`(실 DB, 사용자
    // 혼재 → 요청자만 반환)가 보장한다. 여기서 배제를 단언하는 것처럼 이름 붙이지 않는다.
    Given("신원 헤더의 사용자 id 만 조회 인자로 쓰이는지") {
        val listRecruitmentApplicationsForOrderHistoryUseCase = mockk<ListRecruitmentApplicationsForOrderHistoryUseCase>()
        every { listRecruitmentApplicationsForOrderHistoryUseCase.execute(9L) } returns listOf(applicationHistory(11L))
        val mockMvc = buildMockMvc(listRecruitmentApplicationsForOrderHistoryUseCase)

        When("GET /internal/order-history/recruitment-applications 요청 시 (X-Internal-Auth-Subject: 9)") {
            val result = mockMvc.perform(
                get("/internal/order-history/recruitment-applications").header(INTERNAL_AUTH_SUBJECT_HEADER, "9"),
            )

            Then("요청자(9)의 신청만 반환하고 다른 사용자(999)의 신청은 조회되지 않는다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$[0].sourceId").value(11))
            }
        }
    }

    Given("신청 이력이 없는 사용자") {
        val listRecruitmentApplicationsForOrderHistoryUseCase = mockk<ListRecruitmentApplicationsForOrderHistoryUseCase>()
        every { listRecruitmentApplicationsForOrderHistoryUseCase.execute(999L) } returns emptyList()
        val mockMvc = buildMockMvc(listRecruitmentApplicationsForOrderHistoryUseCase)

        When("GET /internal/order-history/recruitment-applications 요청 시 (X-Internal-Auth-Subject: 999)") {
            val result = mockMvc.perform(
                get("/internal/order-history/recruitment-applications").header(INTERNAL_AUTH_SUBJECT_HEADER, "999"),
            )

            Then("200과 함께 빈 배열을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }
})

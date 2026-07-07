package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.ApplyRecruitmentResult
import com.sportsapp.application.recruitment.dto.CancelRecruitmentCommand
import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.application.recruitment.usecase.ApplyRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.CancelRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.CreateRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.GetRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.ListApplicationsUseCase
import com.sportsapp.application.recruitment.usecase.ListRecruitmentsUseCase
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.exception.RecruitmentFullException
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

class RecruitmentApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        createRecruitmentUseCase: CreateRecruitmentUseCase = mockk(),
        listRecruitmentsUseCase: ListRecruitmentsUseCase = mockk(),
        getRecruitmentUseCase: GetRecruitmentUseCase = mockk(),
        listApplicationsUseCase: ListApplicationsUseCase = mockk(),
        applyRecruitmentUseCase: ApplyRecruitmentUseCase = mockk(),
        cancelRecruitmentUseCase: CancelRecruitmentUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        RecruitmentApiController(
            createRecruitmentUseCase = createRecruitmentUseCase,
            listRecruitmentsUseCase = listRecruitmentsUseCase,
            getRecruitmentUseCase = getRecruitmentUseCase,
            listApplicationsUseCase = listApplicationsUseCase,
            applyRecruitmentUseCase = applyRecruitmentUseCase,
            cancelRecruitmentUseCase = cancelRecruitmentUseCase,
        ),
    ).setControllerAdvice(GlobalExceptionHandler()).build()

    fun recruitmentResponse(id: Long, status: RecruitmentStatus = RecruitmentStatus.OPEN): RecruitmentResponse =
        RecruitmentResponse(
            id = id,
            title = "주말 축구 모임",
            description = "설명",
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = ZonedDateTime.now().plusDays(10),
            applicationDeadline = ZonedDateTime.now().plusDays(5),
            communityId = 1L,
            recruiterUserId = TEST_USER_ID,
            status = status,
        )

    Given("모집 개설 요청") {
        val createRecruitmentUseCase = mockk<CreateRecruitmentUseCase>()
        every { createRecruitmentUseCase.execute(match { it.recruiterUserId == TEST_USER_ID && it.title == "주말 축구 모임" }) } returns
            recruitmentResponse(1L)
        val mockMvc = buildMockMvc(createRecruitmentUseCase = createRecruitmentUseCase)

        When("POST /recruitments 요청 시") {
            val result = mockMvc.perform(
                post("/recruitments")
                    .header("X-User-Id", TEST_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"title":"주말 축구 모임","description":"설명","capacity":10,"feeAmount":10000,
                            |"activityAt":"2026-08-01T10:00:00+09:00","applicationDeadline":"2026-07-25T10:00:00+09:00",
                            |"communityId":1}""".trimMargin(),
                    ),
            )

            Then("200과 함께 RecruitmentResponse를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.status").value("OPEN"))
            }
        }
    }

    Given("모집 목록 조회 요청") {
        val listRecruitmentsUseCase = mockk<ListRecruitmentsUseCase>()
        every { listRecruitmentsUseCase.execute(1L, null) } returns listOf(recruitmentResponse(1L))
        val mockMvc = buildMockMvc(listRecruitmentsUseCase = listRecruitmentsUseCase)

        When("GET /recruitments?communityId=1 요청 시") {
            val result = mockMvc.perform(get("/recruitments").param("communityId", "1"))

            Then("200과 함께 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
            }
        }
    }

    Given("소속 모집이 없는 커뮤니티 조회") {
        val listRecruitmentsUseCase = mockk<ListRecruitmentsUseCase>()
        every { listRecruitmentsUseCase.execute(2L, null) } returns emptyList()
        val mockMvc = buildMockMvc(listRecruitmentsUseCase = listRecruitmentsUseCase)

        When("GET /recruitments?communityId=2 요청 시") {
            val result = mockMvc.perform(get("/recruitments").param("communityId", "2"))

            Then("200과 함께 빈 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }

    Given("PRIVATE 커뮤니티 소속 모집 목록을 비멤버가 조회하면") {
        val listRecruitmentsUseCase = mockk<ListRecruitmentsUseCase>()
        every {
            listRecruitmentsUseCase.execute(3L, TEST_USER_ID)
        } throws NotCommunityMemberException(3L, TEST_USER_ID)
        val mockMvc = buildMockMvc(listRecruitmentsUseCase = listRecruitmentsUseCase)

        When("GET /recruitments?communityId=3 요청 시") {
            val result = mockMvc.perform(
                get("/recruitments").param("communityId", "3").header("X-User-Id", TEST_USER_ID),
            )

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_COMMUNITY_MEMBER"))
            }
        }
    }

    Given("모집 상세 조회 요청") {
        val getRecruitmentUseCase = mockk<GetRecruitmentUseCase>()
        every { getRecruitmentUseCase.execute(1L, null) } returns recruitmentResponse(1L)
        val mockMvc = buildMockMvc(getRecruitmentUseCase = getRecruitmentUseCase)

        When("GET /recruitments/1 요청 시") {
            val result = mockMvc.perform(get("/recruitments/1"))

            Then("200과 함께 상세를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.id").value(1))
            }
        }
    }

    Given("PRIVATE 모임 소속 모집 상세를 비멤버가 조회하면") {
        val getRecruitmentUseCase = mockk<GetRecruitmentUseCase>()
        every {
            getRecruitmentUseCase.execute(4L, TEST_USER_ID)
        } throws NotCommunityMemberException(1L, TEST_USER_ID)
        val mockMvc = buildMockMvc(getRecruitmentUseCase = getRecruitmentUseCase)

        When("GET /recruitments/4 요청 시") {
            val result = mockMvc.perform(get("/recruitments/4").header("X-User-Id", TEST_USER_ID))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_COMMUNITY_MEMBER"))
            }
        }
    }

    Given("X-User-Id 헤더 없이 모집 상세를 조회하면") {
        val getRecruitmentUseCase = mockk<GetRecruitmentUseCase>()
        every { getRecruitmentUseCase.execute(5L, null) } returns recruitmentResponse(5L)
        val mockMvc = buildMockMvc(getRecruitmentUseCase = getRecruitmentUseCase)

        When("GET /recruitments/5 요청 시") {
            val result = mockMvc.perform(get("/recruitments/5"))

            Then("200과 함께 상세를 반환하고 requesterId 는 null 로 전달된다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { getRecruitmentUseCase.execute(5L, null) }
            }
        }
    }

    Given("개설자의 신청자 목록 조회") {
        val listApplicationsUseCase = mockk<ListApplicationsUseCase>()
        every { listApplicationsUseCase.execute(1L, TEST_USER_ID) } returns listOf(
            ApplicationResponse(
                id = 11L,
                recruitmentId = 1L,
                status = ApplicationStatus.CONFIRMED,
                paymentId = 99L,
                appliedAt = ZonedDateTime.now(),
            ),
        )
        val mockMvc = buildMockMvc(listApplicationsUseCase = listApplicationsUseCase)

        When("GET /recruitments/1/applications 요청 시") {
            val result = mockMvc.perform(get("/recruitments/1/applications").header("X-User-Id", TEST_USER_ID))

            Then("200과 함께 신청자 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].id").value(11))
            }
        }
    }

    Given("개설자가 아닌 사용자의 신청자 목록 조회") {
        val listApplicationsUseCase = mockk<ListApplicationsUseCase>()
        every { listApplicationsUseCase.execute(1L, 999L) } throws NotRecruiterException(1L)
        val mockMvc = buildMockMvc(listApplicationsUseCase = listApplicationsUseCase)

        When("GET /recruitments/1/applications 요청 시") {
            val result = mockMvc.perform(get("/recruitments/1/applications").header("X-User-Id", 999L))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_RECRUITER"))
            }
        }
    }

    Given("정원 여유가 있는 유료 모집 신청") {
        val applyRecruitmentUseCase = mockk<ApplyRecruitmentUseCase>()
        every {
            applyRecruitmentUseCase.execute(match { it.recruitmentId == 1L && it.applicantUserId == TEST_USER_ID })
        } returns ApplyRecruitmentResult(
            id = 11L,
            recruitmentId = 1L,
            status = ApplicationStatus.PENDING,
            paymentId = 99L,
            checkoutUrl = "http://checkout/recruitment",
            appliedAt = ZonedDateTime.now(),
        )
        val mockMvc = buildMockMvc(applyRecruitmentUseCase = applyRecruitmentUseCase)

        When("POST /recruitments/1/applications 요청 시") {
            val result = mockMvc.perform(
                post("/recruitments/1/applications")
                    .header("X-User-Id", TEST_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"paymentMethod":"CREDIT_CARD","currency":"KRW"}"""),
            )

            Then("202과 함께 paymentId·checkoutUrl을 반환한다") {
                result.andExpect(status().isAccepted)
                    .andExpect(jsonPath("$.paymentId").value(99))
                    .andExpect(jsonPath("$.checkoutUrl").value("http://checkout/recruitment"))
            }
        }
    }

    Given("정원이 가득 찬 모집 신청") {
        val applyRecruitmentUseCase = mockk<ApplyRecruitmentUseCase>()
        every { applyRecruitmentUseCase.execute(any()) } throws RecruitmentFullException(1L)
        val mockMvc = buildMockMvc(applyRecruitmentUseCase = applyRecruitmentUseCase)

        When("POST /recruitments/1/applications 요청 시") {
            val result = mockMvc.perform(
                post("/recruitments/1/applications")
                    .header("X-User-Id", TEST_USER_ID)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"paymentMethod":"CREDIT_CARD","currency":"KRW"}"""),
            )

            Then("409를 반환한다") {
                result.andExpect(status().isConflict)
                    .andExpect(jsonPath("$.code").value("RECRUITMENT_FULL"))
            }
        }
    }

    Given("개설자의 모집 취소 요청") {
        val cancelRecruitmentUseCase = mockk<CancelRecruitmentUseCase>()
        every {
            cancelRecruitmentUseCase.execute(CancelRecruitmentCommand(recruitmentId = 1L, recruiterUserId = TEST_USER_ID))
        } returns recruitmentResponse(1L, status = RecruitmentStatus.CANCELLED)
        val mockMvc = buildMockMvc(cancelRecruitmentUseCase = cancelRecruitmentUseCase)

        When("POST /recruitments/1/cancel 요청 시") {
            val result = mockMvc.perform(post("/recruitments/1/cancel").header("X-User-Id", TEST_USER_ID))

            Then("200과 함께 CANCELLED 상태를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("CANCELLED"))
            }
        }
    }

    Given("개설자가 아닌 사용자의 모집 취소 요청") {
        val cancelRecruitmentUseCase = mockk<CancelRecruitmentUseCase>()
        every { cancelRecruitmentUseCase.execute(any()) } throws NotRecruiterException(1L)
        val mockMvc = buildMockMvc(cancelRecruitmentUseCase = cancelRecruitmentUseCase)

        When("POST /recruitments/1/cancel 요청 시") {
            val result = mockMvc.perform(post("/recruitments/1/cancel").header("X-User-Id", 999L))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_RECRUITER"))
            }
        }
    }
})

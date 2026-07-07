package com.sportsapp.presentation.community.controller

import com.sportsapp.application.community.dto.CommunityBookingListItemResponse
import com.sportsapp.application.community.dto.CommunityBookingResponse
import com.sportsapp.application.community.usecase.LinkCommunityBookingUseCase
import com.sportsapp.application.community.usecase.ListCommunityBookingsUseCase
import com.sportsapp.domain.community.exception.NotCommunityHostException
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

private const val TEST_USER_ID = 100L

/**
 * standalone MockMvc — [CommunityApiControllerTest] 와 동일 패턴: 실제 Spring Security 필터체인 없이
 * [AuthenticationPrincipal] 파라미터를 고정 [UserPrincipal](TEST_USER_ID)로 해석한다.
 */
private fun fixedPrincipalResolver(userId: Long) = object : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(AuthenticationPrincipal::class.java) &&
            parameter.parameterType == UserPrincipal::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any = UserPrincipal(id = userId, email = "test@sportsapp.local", roles = listOf("USER"))
}

class CommunityBookingApiControllerTest : BehaviorSpec({

    fun buildMockMvc(
        linkCommunityBookingUseCase: LinkCommunityBookingUseCase = mockk(),
        listCommunityBookingsUseCase: ListCommunityBookingsUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        CommunityBookingApiController(linkCommunityBookingUseCase, listCommunityBookingsUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(TEST_USER_ID))
        .build()

    Given("방장의 슬롯 연결 요청") {
        val linkCommunityBookingUseCase = mockk<LinkCommunityBookingUseCase>()
        every { linkCommunityBookingUseCase.execute(match { it.communityId == 1L && it.hostUserId == TEST_USER_ID && it.slotId == 10L }) } returns
            CommunityBookingResponse(
                id = 1L,
                communityId = 1L,
                slotId = 10L,
                linkedByUserId = TEST_USER_ID,
                createdAt = ZonedDateTime.now(),
            )
        val mockMvc = buildMockMvc(linkCommunityBookingUseCase = linkCommunityBookingUseCase)

        When("POST /communities/1/bookings 요청 시") {
            val result = mockMvc.perform(
                post("/communities/1/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"slotId":10}"""),
            )

            Then("200과 함께 CommunityBookingResponse 를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.communityId").value(1))
                    .andExpect(jsonPath("$.slotId").value(10))
            }
        }
    }

    Given("방장이 아닌 사용자의 연결 요청") {
        val linkCommunityBookingUseCase = mockk<LinkCommunityBookingUseCase>()
        every { linkCommunityBookingUseCase.execute(any()) } throws NotCommunityHostException(2L, TEST_USER_ID)
        val mockMvc = buildMockMvc(linkCommunityBookingUseCase = linkCommunityBookingUseCase)

        When("POST /communities/2/bookings 요청 시") {
            val result = mockMvc.perform(
                post("/communities/2/bookings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"slotId":10}"""),
            )

            Then("403 을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_COMMUNITY_HOST"))
            }
        }
    }

    Given("멤버의 연결 목록 조회 요청") {
        val listCommunityBookingsUseCase = mockk<ListCommunityBookingsUseCase>()
        every { listCommunityBookingsUseCase.execute(3L, TEST_USER_ID) } returns listOf(
            CommunityBookingListItemResponse(
                id = 1L,
                communityId = 3L,
                slotId = 10L,
                linkedByUserId = TEST_USER_ID,
                facilityId = "facility-1",
                date = ZonedDateTime.now(),
                timeRange = "10:00-11:00",
                capacity = 8,
            ),
        )
        val mockMvc = buildMockMvc(listCommunityBookingsUseCase = listCommunityBookingsUseCase)

        When("GET /communities/3/bookings 요청 시") {
            val result = mockMvc.perform(get("/communities/3/bookings"))

            Then("200과 함께 SlotInfo 가 포함된 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].facilityId").value("facility-1"))
                    .andExpect(jsonPath("$[0].capacity").value(8))
            }
        }
    }

    Given("연결 예약이 없는 모임 조회") {
        val listCommunityBookingsUseCase = mockk<ListCommunityBookingsUseCase>()
        every { listCommunityBookingsUseCase.execute(4L, TEST_USER_ID) } returns emptyList()
        val mockMvc = buildMockMvc(listCommunityBookingsUseCase = listCommunityBookingsUseCase)

        When("GET /communities/4/bookings 요청 시") {
            val result = mockMvc.perform(get("/communities/4/bookings"))

            Then("200과 함께 빈 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(0))
            }
        }
    }

    Given("PRIVATE 모임 비승인자의 연결 예약 조회") {
        val listCommunityBookingsUseCase = mockk<ListCommunityBookingsUseCase>()
        every {
            listCommunityBookingsUseCase.execute(5L, TEST_USER_ID)
        } throws NotCommunityMemberException(5L, TEST_USER_ID)
        val mockMvc = buildMockMvc(listCommunityBookingsUseCase = listCommunityBookingsUseCase)

        When("GET /communities/5/bookings 요청 시") {
            val result = mockMvc.perform(get("/communities/5/bookings"))

            Then("403 을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_COMMUNITY_MEMBER"))
            }
        }
    }
})

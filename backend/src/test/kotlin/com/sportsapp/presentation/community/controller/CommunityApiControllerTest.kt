package com.sportsapp.presentation.community.controller

import com.sportsapp.application.community.dto.CommunityMemberResponse
import com.sportsapp.application.community.dto.CommunityResponse
import com.sportsapp.application.community.usecase.ApproveMemberUseCase
import com.sportsapp.application.community.usecase.CreateCommunityUseCase
import com.sportsapp.application.community.usecase.GetCommunityUseCase
import com.sportsapp.application.community.usecase.JoinCommunityUseCase
import com.sportsapp.application.community.usecase.KickMemberUseCase
import com.sportsapp.application.community.usecase.LeaveCommunityUseCase
import com.sportsapp.application.community.usecase.ListCommunityMembersUseCase
import com.sportsapp.application.community.usecase.ListMyCommunitiesUseCase
import com.sportsapp.application.community.usecase.ListPublicCommunitiesUseCase
import com.sportsapp.application.community.usecase.TransferHostUseCase
import com.sportsapp.domain.community.exception.AlreadyCommunityMemberException
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.vo.CommunityRole
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.MembershipStatus
import com.sportsapp.domain.community.vo.SportCategory
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
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
 * standalone MockMvc — 실제 Spring Security 필터체인 없이 [AuthenticationPrincipal] 파라미터를
 * 고정된 [UserPrincipal](TEST_USER_ID)로 해석하는 커스텀 리졸버를 등록해 컨트롤러 로직만 검증한다.
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

class CommunityApiControllerTest : BehaviorSpec({

    // CommunityApiController 생성자 파라미터(UseCase 10종)를 그대로 전달하는 테스트 픽스처 —
    // 컨트롤러 자체의 책임 크기이며 로직 복잡도가 아니라 detekt LongParameterList(기본 8)를 억제한다.
    @Suppress("LongParameterList")
    fun buildMockMvc(
        createCommunityUseCase: CreateCommunityUseCase = mockk(),
        joinCommunityUseCase: JoinCommunityUseCase = mockk(),
        approveMemberUseCase: ApproveMemberUseCase = mockk(),
        kickMemberUseCase: KickMemberUseCase = mockk(),
        transferHostUseCase: TransferHostUseCase = mockk(),
        leaveCommunityUseCase: LeaveCommunityUseCase = mockk(),
        getCommunityUseCase: GetCommunityUseCase = mockk(),
        listPublicCommunitiesUseCase: ListPublicCommunitiesUseCase = mockk(),
        listCommunityMembersUseCase: ListCommunityMembersUseCase = mockk(),
        listMyCommunitiesUseCase: ListMyCommunitiesUseCase = mockk(),
    ) = MockMvcBuilders.standaloneSetup(
        CommunityApiController(
            createCommunityUseCase,
            joinCommunityUseCase,
            approveMemberUseCase,
            kickMemberUseCase,
            transferHostUseCase,
            leaveCommunityUseCase,
            getCommunityUseCase,
            listPublicCommunitiesUseCase,
            listCommunityMembersUseCase,
            listMyCommunitiesUseCase,
        ),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(TEST_USER_ID))
        .build()

    fun communityResponse(id: Long = 1L, roomId: Long? = null) = CommunityResponse(
        id = id,
        name = "주말 축구 모임",
        description = null,
        visibility = CommunityVisibility.PUBLIC,
        sportCategory = SportCategory.SOCCER,
        hostUserId = TEST_USER_ID,
        memberCount = 1,
        roomId = roomId,
        createdAt = ZonedDateTime.now(),
    )

    fun communityMemberResponse(status: MembershipStatus = MembershipStatus.ACTIVE) = CommunityMemberResponse(
        id = 1L,
        communityId = 1L,
        userId = TEST_USER_ID,
        role = CommunityRole.MEMBER,
        status = status,
        joinedAt = if (status == MembershipStatus.ACTIVE) ZonedDateTime.now() else null,
    )

    Given("커뮤니티 개설 요청") {
        val createCommunityUseCase = mockk<CreateCommunityUseCase>()
        every { createCommunityUseCase.execute(any()) } returns communityResponse()
        val mockMvc = buildMockMvc(createCommunityUseCase = createCommunityUseCase)

        When("POST /communities 요청 시") {
            val body = """{"name":"주말 축구 모임","description":null,"visibility":"PUBLIC","sportCategory":"SOCCER"}"""
            val result = mockMvc.perform(
                post("/communities").contentType(MediaType.APPLICATION_JSON).content(body),
            )

            Then("200과 함께 CommunityResponse 를 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.name").value("주말 축구 모임"))
                    .andExpect(jsonPath("$.hostUserId").value(TEST_USER_ID))
                verify { createCommunityUseCase.execute(match { it.hostUserId == TEST_USER_ID }) }
            }
        }
    }

    Given("공개 커뮤니티 키워드 목록 조회") {
        val listPublicCommunitiesUseCase = mockk<ListPublicCommunitiesUseCase>()
        every { listPublicCommunitiesUseCase.execute("축구") } returns listOf(communityResponse())
        val mockMvc = buildMockMvc(listPublicCommunitiesUseCase = listPublicCommunitiesUseCase)

        When("GET /communities?keyword=축구 요청 시") {
            val result = mockMvc.perform(get("/communities").param("keyword", "축구"))

            Then("200과 함께 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
            }
        }
    }

    Given("커뮤니티 상세 조회 — 공개") {
        val getCommunityUseCase = mockk<GetCommunityUseCase>()
        every { getCommunityUseCase.execute(1L, TEST_USER_ID) } returns communityResponse(id = 1L, roomId = 5L)
        val mockMvc = buildMockMvc(getCommunityUseCase = getCommunityUseCase)

        When("GET /communities/1 요청 시") {
            val result = mockMvc.perform(get("/communities/1"))

            Then("memberCount·roomId 를 포함해 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.memberCount").value(1))
                    .andExpect(jsonPath("$.roomId").value(5))
            }
        }
    }

    Given("커뮤니티 상세 조회 — 비공개, 비멤버 (FR-13 ②)") {
        val getCommunityUseCase = mockk<GetCommunityUseCase>()
        every { getCommunityUseCase.execute(2L, TEST_USER_ID) } throws NotCommunityMemberException(2L, TEST_USER_ID)
        val mockMvc = buildMockMvc(getCommunityUseCase = getCommunityUseCase)

        When("GET /communities/2 요청 시") {
            val result = mockMvc.perform(get("/communities/2"))

            Then("403 을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_COMMUNITY_MEMBER"))
            }
        }
    }

    Given("내 커뮤니티 목록 조회") {
        val listMyCommunitiesUseCase = mockk<ListMyCommunitiesUseCase>()
        every { listMyCommunitiesUseCase.execute(TEST_USER_ID) } returns listOf(communityResponse())
        val mockMvc = buildMockMvc(listMyCommunitiesUseCase = listMyCommunitiesUseCase)

        When("GET /communities/me 요청 시") {
            val result = mockMvc.perform(get("/communities/me"))

            Then("200과 함께 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
                verify { listMyCommunitiesUseCase.execute(TEST_USER_ID) }
            }
        }
    }

    Given("커뮤니티 멤버 목록 조회 — ACTIVE 멤버") {
        val listCommunityMembersUseCase = mockk<ListCommunityMembersUseCase>()
        every { listCommunityMembersUseCase.execute(3L, TEST_USER_ID) } returns listOf(communityMemberResponse())
        val mockMvc = buildMockMvc(listCommunityMembersUseCase = listCommunityMembersUseCase)

        When("GET /communities/3/members 요청 시") {
            val result = mockMvc.perform(get("/communities/3/members"))

            Then("200과 함께 멤버 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.length()").value(1))
            }
        }
    }

    Given("커뮤니티 멤버 목록 조회 — 비멤버 (FR-13 ②)") {
        val listCommunityMembersUseCase = mockk<ListCommunityMembersUseCase>()
        every {
            listCommunityMembersUseCase.execute(4L, TEST_USER_ID)
        } throws NotCommunityMemberException(4L, TEST_USER_ID)
        val mockMvc = buildMockMvc(listCommunityMembersUseCase = listCommunityMembersUseCase)

        When("GET /communities/4/members 요청 시") {
            val result = mockMvc.perform(get("/communities/4/members"))

            Then("403 을 반환한다") {
                result.andExpect(status().isForbidden)
                    .andExpect(jsonPath("$.code").value("NOT_COMMUNITY_MEMBER"))
            }
        }
    }

    Given("커뮤니티 멤버 목록 조회 — 컨텍스트 방 게스트가 contextId=communityId 로 조회 (FR-13 ②)") {
        val listCommunityMembersUseCase = mockk<ListCommunityMembersUseCase>()
        every {
            listCommunityMembersUseCase.execute(5L, TEST_USER_ID)
        } throws NotCommunityMemberException(5L, TEST_USER_ID)
        val mockMvc = buildMockMvc(listCommunityMembersUseCase = listCommunityMembersUseCase)

        When("GET /communities/5/members 요청 시") {
            val result = mockMvc.perform(get("/communities/5/members"))

            Then("community_members ACTIVE 레코드가 없어 403 으로 거부된다") {
                result.andExpect(status().isForbidden)
            }
        }
    }

    Given("커뮤니티 가입 요청") {
        val joinCommunityUseCase = mockk<JoinCommunityUseCase>()
        every { joinCommunityUseCase.execute(match { it.communityId == 1L && it.userId == TEST_USER_ID }) } returns
            communityMemberResponse()
        val mockMvc = buildMockMvc(joinCommunityUseCase = joinCommunityUseCase)

        When("POST /communities/1/join 요청 시") {
            val result = mockMvc.perform(post("/communities/1/join"))

            Then("200과 함께 ACTIVE 멤버십을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
            }
        }
    }

    Given("이미 ACTIVE 인 사용자의 중복 가입 요청 — 리뷰 p2-①") {
        val joinCommunityUseCase = mockk<JoinCommunityUseCase>()
        every {
            joinCommunityUseCase.execute(match { it.communityId == 1L && it.userId == TEST_USER_ID })
        } throws AlreadyCommunityMemberException(1L, TEST_USER_ID)
        val mockMvc = buildMockMvc(joinCommunityUseCase = joinCommunityUseCase)

        When("POST /communities/1/join 요청 시") {
            val result = mockMvc.perform(post("/communities/1/join"))

            Then("409 를 반환한다 (UNIQUE 제약 500 대신)") {
                result.andExpect(status().isConflict)
                    .andExpect(jsonPath("$.code").value("ALREADY_COMMUNITY_MEMBER"))
            }
        }
    }

    Given("방장의 승인 요청") {
        val approveMemberUseCase = mockk<ApproveMemberUseCase>()
        every { approveMemberUseCase.execute(any()) } returns communityMemberResponse()
        val mockMvc = buildMockMvc(approveMemberUseCase = approveMemberUseCase)

        When("POST /communities/1/members/2/approve 요청 시") {
            val result = mockMvc.perform(post("/communities/1/members/2/approve"))

            Then("200과 함께 ACTIVE 멤버십을 반환한다") {
                result.andExpect(status().isOk)
                verify { approveMemberUseCase.execute(match { it.communityId == 1L && it.targetUserId == 2L && it.requesterId == TEST_USER_ID }) }
            }
        }
    }

    Given("방장의 강퇴 요청") {
        val kickMemberUseCase = mockk<KickMemberUseCase>()
        justRun { kickMemberUseCase.execute(any()) }
        val mockMvc = buildMockMvc(kickMemberUseCase = kickMemberUseCase)

        When("POST /communities/1/members/2/kick 요청 시") {
            val result = mockMvc.perform(post("/communities/1/members/2/kick"))

            Then("204 를 반환한다") {
                result.andExpect(status().isNoContent)
                verify { kickMemberUseCase.execute(match { it.communityId == 1L && it.targetUserId == 2L }) }
            }
        }
    }

    Given("방장 권한 위임 요청") {
        val transferHostUseCase = mockk<TransferHostUseCase>()
        justRun { transferHostUseCase.execute(any()) }
        val mockMvc = buildMockMvc(transferHostUseCase = transferHostUseCase)

        When("POST /communities/1/host/transfer 요청 시") {
            val result = mockMvc.perform(
                post("/communities/1/host/transfer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"newHostUserId":2}"""),
            )

            Then("200을 반환한다") {
                result.andExpect(status().isOk)
                verify { transferHostUseCase.execute(match { it.communityId == 1L && it.newHostUserId == 2L }) }
            }
        }
    }

    Given("일반 멤버의 탈퇴 요청") {
        val leaveCommunityUseCase = mockk<LeaveCommunityUseCase>()
        justRun { leaveCommunityUseCase.execute(any()) }
        val mockMvc = buildMockMvc(leaveCommunityUseCase = leaveCommunityUseCase)

        When("DELETE /communities/1/members/me 요청 시") {
            val result = mockMvc.perform(delete("/communities/1/members/me"))

            Then("204 를 반환한다") {
                result.andExpect(status().isNoContent)
                verify { leaveCommunityUseCase.execute(match { it.communityId == 1L && it.userId == TEST_USER_ID }) }
            }
        }
    }
})

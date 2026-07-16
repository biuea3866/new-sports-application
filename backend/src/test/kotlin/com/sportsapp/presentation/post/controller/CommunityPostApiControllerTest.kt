package com.sportsapp.presentation.post.controller

import com.sportsapp.application.post.usecase.ListCommunityPostsUseCase
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

class CommunityPostApiControllerTest : BehaviorSpec({

    fun buildMockMvc(listCommunityPostsUseCase: ListCommunityPostsUseCase = mockk(), userId: Long? = null) =
        MockMvcBuilders.standaloneSetup(CommunityPostApiController(listCommunityPostsUseCase))
            .setControllerAdvice(GlobalExceptionHandler())
            .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
            .build()

    Given("인증 없이 PUBLIC 모임의 게시글 목록을 조회하면") {
        val listCommunityPostsUseCase = mockk<ListCommunityPostsUseCase>()
        every {
            listCommunityPostsUseCase.execute(communityId = 10L, requesterId = null, sportCategory = null, page = 0, size = 20)
        } returns PageImpl(emptyList<Post>(), PageRequest.of(0, 20), 0)
        val mockMvc = buildMockMvc(listCommunityPostsUseCase)

        When("GET /communities/10/posts 요청 시") {
            val result = mockMvc.perform(get("/communities/10/posts"))

            Then("200과 빈 목록을 반환한다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.content.length()").value(0))
            }
        }
    }

    Given("PRIVATE 모임의 게시글 목록을 비멤버가 조회하면") {
        val listCommunityPostsUseCase = mockk<ListCommunityPostsUseCase>()
        every {
            listCommunityPostsUseCase.execute(communityId = 20L, requesterId = TEST_USER_ID, sportCategory = null, page = 0, size = 20)
        } throws NotCommunityMemberException(20L, TEST_USER_ID)
        val mockMvc = buildMockMvc(listCommunityPostsUseCase, userId = TEST_USER_ID)

        When("GET /communities/20/posts 요청 시") {
            val result = mockMvc.perform(get("/communities/20/posts"))

            Then("403을 반환한다") {
                result.andExpect(status().isForbidden)
            }
        }
    }
})

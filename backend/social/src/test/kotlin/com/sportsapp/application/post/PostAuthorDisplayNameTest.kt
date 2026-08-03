package com.sportsapp.application.post

import com.sportsapp.application.post.dto.PostCriteria
import com.sportsapp.application.post.usecase.GetPostUseCase
import com.sportsapp.application.post.usecase.ListCommentsUseCase
import com.sportsapp.application.post.usecase.SearchPostsUseCase
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.CommentDomainService
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.post.vo.PostType
import com.sportsapp.domain.user.dto.UserDisplayNames
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

/**
 * 커뮤니티 게시글 화면이 `사용자 71` 대신 작성자 표시 이름을 보여주기 위한 조합. post 도메인은
 * user 를 모른 채로 두고, application 레이어가 UserDomainService 로 조회해 응답에 싣는다.
 */
class PostAuthorDisplayNameTest : BehaviorSpec({

    val postDomainService = mockk<PostDomainService>()
    val commentDomainService = mockk<CommentDomainService>()
    val communityDomainService = mockk<CommunityDomainService>()
    val userDomainService = mockk<UserDomainService>()

    fun displayNamesOf(vararg pairs: Pair<Long, String>): UserDisplayNames =
        UserDisplayNames.from(
            pairs.map { (userId, displayName) ->
                mockk<User>().also {
                    every { it.id } returns userId
                    every { it.displayName } returns displayName
                }
            },
        )

    fun post(id: Long, authorUserId: Long): Post {
        val post = mockk<Post>(relaxed = true)
        every { post.id } returns id
        every { post.userId } returns authorUserId
        every { post.title } returns "제목 $id"
        every { post.content } returns "본문 $id"
        every { post.type } returns PostType.FREE
        every { post.createdAt } returns ZonedDateTime.now()
        every { post.currentCommunityId } returns null
        every { post.currentSportCategory } returns null
        return post
    }

    fun comment(id: Long, authorUserId: Long): Comment {
        val comment = mockk<Comment>(relaxed = true)
        every { comment.id } returns id
        every { comment.postId } returns 1L
        every { comment.userId } returns authorUserId
        every { comment.content } returns "댓글 $id"
        every { comment.createdAt } returns ZonedDateTime.now()
        return comment
    }

    Given("작성자가 서로 다른 게시글 2건") {
        val searchPostsUseCase = SearchPostsUseCase(postDomainService, userDomainService)
        val posts = listOf(post(1L, 71L), post(2L, 68L))
        every { postDomainService.search(any(), any()) } returns PageImpl(posts, PageRequest.of(0, 20), 2)
        every { userDomainService.findDisplayNamesBy(listOf(71L, 68L)) } returns
            displayNamesOf(71L to "김철수", 68L to "박영희")

        When("전역 피드를 조회하면") {
            val page = searchPostsUseCase.execute(PostCriteria(type = null, userId = null, keyword = null, page = 0, size = 20))

            Then("작성자 표시 이름이 함께 반환된다") {
                page.content.map { it.userId } shouldBe listOf(71L, 68L)
                page.content.map { it.authorDisplayName } shouldBe listOf("김철수", "박영희")
            }

            Then("표시 이름 조회는 게시글 수와 무관하게 1회다 (N+1 없음)") {
                verify(exactly = 1) { userDomainService.findDisplayNamesBy(listOf(71L, 68L)) }
            }
        }
    }

    Given("닉네임을 설정하지 않은 작성자의 게시글") {
        val searchPostsUseCase = SearchPostsUseCase(postDomainService, userDomainService)
        every { postDomainService.search(any(), any()) } returns
            PageImpl(listOf(post(3L, 99L)), PageRequest.of(0, 20), 1)
        every { userDomainService.findDisplayNamesBy(listOf(99L)) } returns UserDisplayNames.from(emptyList())

        When("전역 피드를 조회하면") {
            val page = searchPostsUseCase.execute(PostCriteria(type = null, userId = null, keyword = null, page = 0, size = 20))

            Then("이메일·내부 식별자 대신 기본 표시 이름을 반환한다") {
                page.content.single().authorDisplayName shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
            }
        }
    }

    Given("작성자와 댓글 작성자가 다른 게시글 상세") {
        val getPostUseCase = GetPostUseCase(postDomainService, communityDomainService, userDomainService)
        every { postDomainService.getDetail(1L) } returns (post(1L, 55L) to listOf(comment(10L, 66L)))
        every { userDomainService.findDisplayNamesBy(listOf(55L, 66L)) } returns
            displayNamesOf(55L to "이민수", 66L to "최지우")

        When("상세를 조회하면") {
            val detail = getPostUseCase.execute(postId = 1L, requesterId = 5L)

            Then("게시글·댓글 작성자 이름을 한 번의 조회로 모두 채운다") {
                detail.authorDisplayName shouldBe "이민수"
                detail.comments.single().authorDisplayName shouldBe "최지우"
                verify(exactly = 1) { userDomainService.findDisplayNamesBy(listOf(55L, 66L)) }
            }
        }
    }

    Given("댓글 2건이 달린 게시글") {
        val listCommentsUseCase = ListCommentsUseCase(
            postDomainService,
            commentDomainService,
            communityDomainService,
            userDomainService,
        )
        every { postDomainService.findPost(1L) } returns null
        every { commentDomainService.listComments(postId = 1L, page = 0, size = 20) } returns
            PageImpl(listOf(comment(10L, 41L), comment(11L, 42L)), PageRequest.of(0, 20), 2)
        every { userDomainService.findDisplayNamesBy(listOf(41L, 42L)) } returns
            displayNamesOf(41L to "김철수", 42L to "박영희")

        When("댓글 목록을 조회하면") {
            val page = listCommentsUseCase.execute(postId = 1L, requesterId = 5L, page = 0, size = 20)

            Then("댓글 작성자 표시 이름이 함께 반환된다") {
                page.content.map { it.authorDisplayName } shouldBe listOf("김철수", "박영희")
            }
        }
    }
})

package com.sportsapp.domain.post.service

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.exception.NoticeRequiresHostException
import com.sportsapp.domain.post.repository.CommentRepository
import com.sportsapp.domain.post.repository.PostCustomRepository
import com.sportsapp.domain.post.repository.PostRepository
import com.sportsapp.domain.post.vo.PostType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class PostDomainServiceTest : BehaviorSpec({

    fun newService(): Triple<PostRepository, PostDomainService, CommentRepository> {
        val postRepository = mockk<PostRepository>()
        val commentRepository = mockk<CommentRepository>()
        val postCustomRepository = mockk<PostCustomRepository>()
        every { postRepository.save(any()) } answers { firstArg() }
        return Triple(postRepository, PostDomainService(postRepository, commentRepository, postCustomRepository), commentRepository)
    }

    Given("type·sportCategory 없이 createPost 를 호출하면") {
        val (postRepository, service, _) = newService()

        When("실행하면") {
            val post = service.createPost(userId = 1L, title = "제목", content = "내용")

            Then("전역 FREE 게시글이 생성되고 저장된다") {
                post.userId shouldBe 1L
                post.type shouldBe PostType.FREE
                post.currentCommunityId shouldBe null
                post.currentSportCategory shouldBe null
                post.isGlobalListed shouldBe true
                verify(exactly = 1) { postRepository.save(any()) }
            }
        }
    }

    Given("type·sportCategory 를 지정해 createPost 를 호출하면") {
        val (_, service, _) = newService()

        When("실행하면") {
            val post = service.createPost(
                userId = 1L,
                title = "제목",
                content = "내용",
                type = PostType.QUESTION,
                sportCategory = SportCategory.SOCCER,
            )

            Then("지정한 type·sportCategory 로 전역 게시글이 생성된다") {
                post.type shouldBe PostType.QUESTION
                post.currentSportCategory shouldBe SportCategory.SOCCER
                post.currentCommunityId shouldBe null
            }
        }
    }

    Given("모임 게시글을 방장이 NOTICE 로 작성하면") {
        val (postRepository, service, _) = newService()

        When("createCommunityPost 를 호출하면") {
            val post = service.createCommunityPost(
                userId = 1L,
                title = "공지",
                content = "이번 주 공지",
                type = PostType.NOTICE,
                communityId = 10L,
                sportCategory = SportCategory.SOCCER,
                authorIsHost = true,
                communityIsPublic = true,
            )

            Then("게시글이 정상 생성되고 저장된다") {
                post.currentCommunityId shouldBe 10L
                post.currentSportCategory shouldBe SportCategory.SOCCER
                post.isGlobalListed shouldBe true
                verify(exactly = 1) { postRepository.save(any()) }
            }
        }
    }

    Given("모임 게시글을 비방장이 NOTICE 로 작성하면") {
        val (postRepository, service, _) = newService()

        Then("NoticeRequiresHostException 을 던지고 저장하지 않는다") {
            shouldThrow<NoticeRequiresHostException> {
                service.createCommunityPost(
                    userId = 2L,
                    title = "공지",
                    content = "이번 주 공지",
                    type = PostType.NOTICE,
                    communityId = 10L,
                    sportCategory = SportCategory.SOCCER,
                    authorIsHost = false,
                    communityIsPublic = true,
                )
            }
            verify(exactly = 0) { postRepository.save(any()) }
        }
    }

    Given("비공개 모임 게시글을 작성하면") {
        val (_, service, _) = newService()

        When("createCommunityPost 를 호출하면") {
            val post = service.createCommunityPost(
                userId = 1L,
                title = "제목",
                content = "내용",
                type = PostType.FREE,
                communityId = 20L,
                sportCategory = SportCategory.TENNIS,
                authorIsHost = true,
                communityIsPublic = false,
            )

            Then("globalListed 가 false 인 채로 저장된다") {
                post.isGlobalListed shouldBe false
            }
        }
    }
})

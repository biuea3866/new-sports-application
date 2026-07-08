package com.sportsapp.domain.post

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.exception.NoticeRequiresHostException
import com.sportsapp.domain.post.vo.PostType

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class PostTest : BehaviorSpec({

    Given("유효한 정보로 Post.create 를 호출하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")

        Then("[U-01] Post 가 생성된다") {
            post.userId shouldBe 1L
            post.title shouldBe "제목"
            post.content shouldBe "내용"
        }
    }

    Given("생성된 Post 에 changePost 를 호출하면") {
        val post = Post.create(userId = 1L, title = "원래 제목", content = "원래 내용")

        When("유효한 제목과 내용을 전달하면") {
            post.changePost(title = "새 제목", content = "새 내용")
            Then("[U-02] 제목과 내용이 변경된다") {
                post.title shouldBe "새 제목"
                post.content shouldBe "새 내용"
            }
        }
    }

    Given("Post.create 에 빈 제목을 전달하면") {
        Then("[U-02] IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                Post.create(userId = 1L, title = "", content = "내용")
            }
        }
    }

    Given("Post.create 에 201자 제목을 전달하면") {
        val longTitle = "a".repeat(201)
        Then("[U-02] IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                Post.create(userId = 1L, title = longTitle, content = "내용")
            }
        }
    }

    Given("Post.create 에 빈 내용을 전달하면") {
        Then("[U-02] IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                Post.create(userId = 1L, title = "제목", content = "")
            }
        }
    }

    Given("Post.create 에 10001자 내용을 전달하면") {
        val longContent = "a".repeat(10001)
        Then("[U-02] IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                Post.create(userId = 1L, title = "제목", content = longContent)
            }
        }
    }

    Given("생성된 Post 에 softDelete 를 호출하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")

        When("userId 를 전달하면") {
            post.softDelete(1L)
            Then("[U-03] deletedAt 이 채워지고 isDeleted 가 true 가 된다") {
                post.isDeleted shouldBe true
                post.deletedAt shouldNotBe null
                post.deletedBy shouldBe 1L
            }
        }
    }

    Given("이미 삭제된 Post 에 softDelete 를 다시 호출하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        post.softDelete(1L)

        Then("[U-03] IllegalStateException 을 던진다") {
            shouldThrow<IllegalStateException> {
                post.softDelete(1L)
            }
        }
    }

    Given("Post 에 addComment 를 호출하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")

        When("유효한 userId 와 content 를 전달하면") {
            val comment = post.addComment(userId = 2L, content = "댓글")

            Then("Comment 가 생성되고 post.comments 컬렉션에 추가되며 양방향 참조가 설정된다") {
                comment.post shouldBe post
                comment.userId shouldBe 2L
                comment.content shouldBe "댓글"
                post.comments.size shouldBe 1
                post.comments.first() shouldBe comment
            }
        }
    }

    Given("Post 에 addComment 를 빈 content 로 호출하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")

        Then("IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                post.addComment(userId = 2L, content = "")
            }
        }
    }

    Given("Post.create 를 sportCategory 없이 호출하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")

        Then("전역 게시글로 생성되어 communityId 는 null, globalListed 는 true 다") {
            post.currentCommunityId shouldBe null
            post.currentSportCategory shouldBe null
            post.isGlobalListed shouldBe true
        }
    }

    Given("Post.create 를 sportCategory 를 지정해 호출하면") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용", sportCategory = SportCategory.SOCCER)

        Then("전역 게시글이면서도 sportCategory 가 저장된다") {
            post.currentCommunityId shouldBe null
            post.currentSportCategory shouldBe SportCategory.SOCCER
            post.isGlobalListed shouldBe true
        }
    }

    Given("모임 소속 게시글을 NOTICE 타입 + 방장으로 작성하면") {
        val post = Post.createInCommunity(
            userId = 1L,
            title = "공지",
            content = "이번 주 공지사항",
            type = PostType.NOTICE,
            communityId = 10L,
            sportCategory = SportCategory.SOCCER,
            authorIsHost = true,
            communityIsPublic = true,
        )

        Then("게시글이 정상 생성되고 communityId·sportCategory 가 상속된다") {
            post.currentCommunityId shouldBe 10L
            post.currentSportCategory shouldBe SportCategory.SOCCER
            post.isGlobalListed shouldBe true
        }
    }

    Given("모임 소속 게시글을 NOTICE 타입 + 비방장으로 작성하면") {
        Then("NoticeRequiresHostException 이 발생한다") {
            shouldThrow<NoticeRequiresHostException> {
                Post.createInCommunity(
                    userId = 2L,
                    title = "공지",
                    content = "이번 주 공지사항",
                    type = PostType.NOTICE,
                    communityId = 10L,
                    sportCategory = SportCategory.SOCCER,
                    authorIsHost = false,
                    communityIsPublic = true,
                )
            }
        }
    }

    Given("모임 소속 게시글을 FREE 타입 + 비방장으로 작성하면") {
        val post = Post.createInCommunity(
            userId = 2L,
            title = "잡담",
            content = "오늘 날씨가 좋네요",
            type = PostType.FREE,
            communityId = 10L,
            sportCategory = SportCategory.BASKETBALL,
            authorIsHost = false,
            communityIsPublic = true,
        )

        Then("host 여부와 무관하게 정상 생성된다") {
            post.currentCommunityId shouldBe 10L
            post.currentSportCategory shouldBe SportCategory.BASKETBALL
        }
    }

    Given("비공개 모임 소속 게시글을 작성하면") {
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 20L,
            sportCategory = SportCategory.TENNIS,
            authorIsHost = true,
            communityIsPublic = false,
        )

        Then("globalListed 가 false 다") {
            post.isGlobalListed shouldBe false
        }
    }

    Given("공개 모임 소속 게시글을 작성하면") {
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 20L,
            sportCategory = SportCategory.TENNIS,
            authorIsHost = true,
            communityIsPublic = true,
        )

        Then("globalListed 가 true 다") {
            post.isGlobalListed shouldBe true
        }
    }
})

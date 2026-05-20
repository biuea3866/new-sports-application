package com.sportsapp.domain.post

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
})

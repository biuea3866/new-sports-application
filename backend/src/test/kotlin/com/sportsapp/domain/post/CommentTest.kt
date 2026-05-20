package com.sportsapp.domain.post

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CommentTest : BehaviorSpec({

    Given("유효한 정보로 Comment.create 를 호출하면") {
        val comment = Comment.create(postId = 1L, userId = 2L, content = "댓글 내용")

        Then("[U-01] Comment 가 생성된다") {
            comment.postId shouldBe 1L
            comment.userId shouldBe 2L
            comment.content shouldBe "댓글 내용"
        }
    }

    Given("생성된 Comment 에 changeContent 를 호출하면") {
        val comment = Comment.create(postId = 1L, userId = 2L, content = "원래 댓글")

        When("유효한 내용을 전달하면") {
            comment.changeContent("새 댓글")
            Then("[U-02] 내용이 변경된다") {
                comment.content shouldBe "새 댓글"
            }
        }
    }

    Given("Comment.create 에 빈 내용을 전달하면") {
        Then("[U-02] IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                Comment.create(postId = 1L, userId = 2L, content = "")
            }
        }
    }

    Given("Comment.create 에 2001자 내용을 전달하면") {
        val longContent = "a".repeat(2001)
        Then("[U-02] IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                Comment.create(postId = 1L, userId = 2L, content = longContent)
            }
        }
    }

    Given("생성된 Comment 에 softDelete 를 호출하면") {
        val comment = Comment.create(postId = 1L, userId = 2L, content = "댓글")

        When("userId 를 전달하면") {
            comment.softDelete(2L)
            Then("[U-03] deletedAt 이 채워지고 isDeleted 가 true 가 된다") {
                comment.isDeleted shouldBe true
                comment.deletedAt shouldNotBe null
                comment.deletedBy shouldBe 2L
            }
        }
    }
})

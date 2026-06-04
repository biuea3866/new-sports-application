package com.sportsapp.domain.post

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class BE10CommentDeletePropagationTest : BehaviorSpec({

    Given("[U-01] Comment.delete에 소유자가 아닌 userId 전달 시") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val comment = Comment.create(post = post, userId = 2L, content = "댓글")

        When("다른 userId로 delete를 호출하면") {
            Then("[U-01] NotCommentOwnerException을 던진다") {
                shouldThrow<NotCommentOwnerException> {
                    comment.delete(99L)
                }
            }
        }
    }

    Given("[U-02] Comment.softDelete는 소유자 검증 없이 deletedAt을 설정한다") {
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val comment = Comment.create(post = post, userId = 2L, content = "댓글")

        When("소유자가 아닌 userId로 softDelete를 호출하면") {
            comment.softDelete(999L)

            Then("[U-02] deletedAt이 설정되고 isDeleted가 true가 된다") {
                comment.isDeleted shouldBe true
                comment.deletedAt shouldNotBe null
                comment.deletedBy shouldBe 999L
            }
        }
    }
})

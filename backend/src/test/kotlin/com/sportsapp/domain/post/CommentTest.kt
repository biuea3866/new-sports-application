package com.sportsapp.domain.post

import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post

import com.sportsapp.domain.post.exception.NotCommentOwnerException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class CommentTest : BehaviorSpec({

    val post = Post.create(userId = 1L, title = "제목", content = "내용")

    Given("유효한 정보로 Comment.create 를 호출하면") {
        val comment = Comment.create(post = post, userId = 2L, content = "댓글 내용")

        Then("[U-01] Comment 가 생성되고 post 참조와 userId 가 올바르게 설정된다") {
            comment.post shouldBe post
            comment.userId shouldBe 2L
            comment.content shouldBe "댓글 내용"
        }
    }

    Given("생성된 Comment 에 changeContent 를 호출하면") {
        val comment = Comment.create(post = post, userId = 2L, content = "원래 댓글")

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
                Comment.create(post = post, userId = 2L, content = "")
            }
        }
    }

    Given("Comment.create 에 2001자 내용을 전달하면") {
        val longContent = "a".repeat(2001)
        Then("[U-02] IllegalArgumentException 을 던진다") {
            shouldThrow<IllegalArgumentException> {
                Comment.create(post = post, userId = 2L, content = longContent)
            }
        }
    }

    Given("생성된 Comment 에 softDelete 를 호출하면") {
        val comment = Comment.create(post = post, userId = 2L, content = "댓글")

        When("userId 를 전달하면") {
            comment.softDelete(2L)
            Then("[U-03] deletedAt 이 채워지고 isDeleted 가 true 가 된다") {
                comment.isDeleted shouldBe true
                comment.deletedAt shouldNotBe null
                comment.deletedBy shouldBe 2L
            }
        }
    }

    Given("본인 댓글에 delete 를 호출하면") {
        val comment = Comment.create(post = post, userId = 2L, content = "댓글")

        When("본인 userId 를 전달하면") {
            comment.delete(2L)
            Then("[U-02] 소프트 삭제되고 isDeleted 가 true 가 된다") {
                comment.isDeleted shouldBe true
                comment.deletedBy shouldBe 2L
            }
        }
    }

    Given("타인 댓글에 delete 를 호출하면") {
        val comment = Comment.create(post = post, userId = 2L, content = "댓글")

        When("다른 userId 를 전달하면") {
            Then("[U-02] NotCommentOwnerException 을 던진다") {
                shouldThrow<NotCommentOwnerException> {
                    comment.delete(99L)
                }
            }
        }
    }
})

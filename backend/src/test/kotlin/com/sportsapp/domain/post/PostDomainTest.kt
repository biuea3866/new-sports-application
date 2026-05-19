package com.sportsapp.domain.post

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.ZonedDateTime

class PostDomainTest : BehaviorSpec({

    val now = ZonedDateTime.now()

    Given("빈 title이 입력된 경우") {
        When("Post.create를 호출하면") {
            Then("[U-01] InvalidPostException을 던진다") {
                shouldThrow<InvalidPostException> {
                    Post.create(
                        type = PostType.FREE,
                        title = "",
                        content = "content",
                        userId = 1L,
                        writer = "writer",
                        createdAt = now,
                    )
                }
            }
        }
    }

    Given("공백만 있는 title이 입력된 경우") {
        When("Post.create를 호출하면") {
            Then("[U-01] InvalidPostException을 던진다") {
                shouldThrow<InvalidPostException> {
                    Post.create(
                        type = PostType.FREE,
                        title = "   ",
                        content = "content",
                        userId = 1L,
                        writer = "writer",
                        createdAt = now,
                    )
                }
            }
        }
    }

    Given("정상 입력으로 생성된 Post") {
        val post = Post.create(
            type = PostType.FREE,
            title = "제목",
            content = "내용",
            userId = 1L,
            writer = "작성자",
            createdAt = now,
        )

        When("본인(userId=1)이 changeContent를 호출하면") {
            val updated = post.changeContent(
                requestingUserId = 1L,
                newTitle = "변경된 제목",
                newContent = "변경된 내용",
            )
            Then("[U-02] 변경이 성공하고 새 Post 반환된다") {
                updated.title shouldBe "변경된 제목"
                updated.content shouldBe "변경된 내용"
                updated.userId shouldBe 1L
            }
        }

        When("타인(userId=99)이 changeContent를 호출하면") {
            Then("[U-02] NotPostOwnerException을 던진다") {
                shouldThrow<NotPostOwnerException> {
                    post.changeContent(
                        requestingUserId = 99L,
                        newTitle = "변경된 제목",
                        newContent = "변경된 내용",
                    )
                }
            }
        }
    }
})

class CommentDomainTest : BehaviorSpec({

    val now = ZonedDateTime.now()

    Given("1001자짜리 content로 Comment 생성 시도") {
        val longContent = "a".repeat(1001)
        When("Comment.create를 호출하면") {
            Then("[U-03] CommentTooLongException을 던진다") {
                shouldThrow<CommentTooLongException> {
                    Comment.create(
                        postId = "post-1",
                        content = longContent,
                        userId = 1L,
                        writer = "writer",
                        createdAt = now,
                    )
                }
            }
        }
    }

    Given("정상 길이의 content") {
        val content = "a".repeat(1000)
        When("Comment.create를 호출하면") {
            val comment = Comment.create(
                postId = "post-1",
                content = content,
                userId = 1L,
                writer = "writer",
                createdAt = now,
            )
            Then("[U-03] Comment가 생성된다") {
                comment.content.length shouldBe 1000
                comment.postId shouldBe "post-1"
            }
        }
    }
})

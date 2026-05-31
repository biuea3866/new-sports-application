package com.sportsapp.infrastructure.persistence.post

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.post.Comment
import com.sportsapp.domain.post.CommentRepository
import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostDomainService
import com.sportsapp.domain.post.PostRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class BE10PostCommentOrphanTest(
    @Autowired private val commentRepository: CommentRepository,
    @Autowired private val postRepository: PostRepository,
    @Autowired private val postDomainService: PostDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM comments")
            jdbcTemplate.execute("DELETE FROM posts")
        }

        Given("[R-01] findAllActiveByPostId로 가져온 댓글을 softDelete 후 saveAll하면 findByPostId는 0건을 반환한다") {
            val post = postRepository.save(Post.create(userId = 1L, title = "제목", content = "본문"))
            commentRepository.save(Comment.create(postId = post.id, userId = 2L, content = "댓글1"))
            commentRepository.save(Comment.create(postId = post.id, userId = 3L, content = "댓글2"))

            When("[R-01] findAllActiveByPostId + forEach softDelete + saveAll 호출하면") {
                val comments = commentRepository.findAllActiveByPostId(post.id)
                comments.forEach { it.softDelete(1L) }
                commentRepository.saveAll(comments)

                Then("[R-01] findByPostId가 0건을 반환한다") {
                    commentRepository.findByPostId(post.id).size shouldBe 0
                }
            }
        }

        Given("[R-02] softDelete는 다른 postId의 Comment에 영향을 주지 않는다") {
            val postA = postRepository.save(Post.create(userId = 1L, title = "포스트A", content = "내용"))
            val postB = postRepository.save(Post.create(userId = 1L, title = "포스트B", content = "내용"))
            commentRepository.save(Comment.create(postId = postA.id, userId = 2L, content = "A댓글"))
            commentRepository.save(Comment.create(postId = postB.id, userId = 2L, content = "B댓글"))

            When("[R-02] postA 댓글만 softDelete하면") {
                val comments = commentRepository.findAllActiveByPostId(postA.id)
                comments.forEach { it.softDelete(1L) }
                commentRepository.saveAll(comments)

                Then("[R-02] postB의 댓글은 여전히 1건 조회된다") {
                    commentRepository.findByPostId(postA.id).size shouldBe 0
                    commentRepository.findByPostId(postB.id).size shouldBe 1
                }
            }
        }

        Given("[S-01][S-02] deletePost 후 해당 Post의 모든 Comment가 soft-delete된다 (루트 soft-delete → 자식 조회 0건)") {
            val post = postRepository.save(Post.create(userId = 1L, title = "삭제될 포스트", content = "본문"))
            commentRepository.save(Comment.create(postId = post.id, userId = 2L, content = "댓글A"))
            commentRepository.save(Comment.create(postId = post.id, userId = 3L, content = "댓글B"))

            When("[S-01][S-02] deletePost를 호출하면") {
                postDomainService.deletePost(post.id, 1L)

                Then("[S-01][S-02] commentRepository.findByPostId(postId)가 0건을 반환한다") {
                    commentRepository.findByPostId(post.id).size shouldBe 0
                }
            }
        }

        Given("[S-03] Post A 삭제 시 Post B의 댓글은 deletedAt IS NULL을 유지한다") {
            val postA = postRepository.save(Post.create(userId = 1L, title = "삭제포스트A", content = "내용"))
            val postB = postRepository.save(Post.create(userId = 1L, title = "포스트B", content = "내용"))
            commentRepository.save(Comment.create(postId = postA.id, userId = 2L, content = "A댓글"))
            commentRepository.save(Comment.create(postId = postB.id, userId = 2L, content = "B댓글"))

            When("[S-03] postA를 deletePost로 삭제하면") {
                postDomainService.deletePost(postA.id, 1L)

                Then("[S-03] postB의 댓글은 여전히 1건 조회된다") {
                    commentRepository.findByPostId(postB.id).size shouldBe 1
                }
            }
        }
    }
}

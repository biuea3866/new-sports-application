package com.sportsapp.domain.post.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.dto.PostSearchCriteria
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.repository.CommentRepository
import com.sportsapp.domain.post.repository.PostCustomRepository
import com.sportsapp.domain.post.repository.PostRepository
import com.sportsapp.domain.post.vo.PostType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.transaction.annotation.Transactional

@Service
class PostDomainService(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val postCustomRepository: PostCustomRepository,
) {
    fun createPost(
        userId: Long,
        title: String,
        content: String,
        type: PostType = PostType.FREE,
        sportCategory: SportCategory? = null,
    ): Post {
        val post = Post.create(userId = userId, title = title, content = content, type = type, sportCategory = sportCategory)
        return postRepository.save(post)
    }

    /**
     * 모임 소속 게시글을 생성한다. NOTICE 규칙·종목 상속·전역 노출 여부는 [Post.createInCommunity]
     * 내부에서 판정하며, 이 메서드는 community 인가 결과를 primitive 로만 전달받는다(R1).
     */
    fun createCommunityPost(
        userId: Long,
        title: String,
        content: String,
        type: PostType,
        communityId: Long,
        sportCategory: SportCategory?,
        authorIsHost: Boolean,
        communityIsPublic: Boolean,
    ): Post {
        val post = Post.createInCommunity(
            userId = userId,
            title = title,
            content = content,
            type = type,
            communityId = communityId,
            sportCategory = sportCategory,
            authorIsHost = authorIsHost,
            communityIsPublic = communityIsPublic,
        )
        return postRepository.save(post)
    }

    fun changePost(postId: Long, title: String, content: String): Post {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        post.changePost(title = title, content = content)
        return postRepository.save(post)
    }

    fun deletePost(postId: Long, userId: Long): Post {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        post.softDelete(userId)
        postRepository.save(post)
        val comments = commentRepository.findAllActiveByPostId(postId)
        comments.forEach { it.softDelete(userId) }
        if (comments.isNotEmpty()) commentRepository.saveAll(comments)
        return post
    }

    fun getPost(postId: Long): Post =
        postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)

    // 없거나 소프트 삭제된 Post 는 null. Post 존재를 강제하지 않아야 하는 조회 경로
    // (listComments 는 Post 삭제 여부를 검증하지 않는다)의 가시성 재판정에 사용한다.
    fun findPost(postId: Long): Post? = postRepository.findById(postId)

    fun search(criteria: PostSearchCriteria, pageable: Pageable): Page<Post> =
        postCustomRepository.findByCriteria(criteria, pageable)

    @Transactional(readOnly = true)
    fun getDetail(postId: Long): Pair<Post, List<Comment>> {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        val comments = commentRepository.findTop50ByPostId(postId)
        return Pair(post, comments)
    }

    fun addComment(postId: Long, userId: Long, content: String): Comment {
        val post = postRepository.findById(postId)
            ?: throw ResourceNotFoundException("Post", postId)
        val comment = post.addComment(userId = userId, content = content)
        return commentRepository.save(comment)
    }

    fun deleteComment(commentId: Long, requestUserId: Long) {
        val comment = commentRepository.findById(commentId)
            ?: throw ResourceNotFoundException("Comment", commentId)
        comment.delete(requestUserId)
        commentRepository.save(comment)
    }

    fun listComments(postId: Long, page: Int, size: Int): Page<Comment> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "createdAt"))
        return commentRepository.findPageByPostId(postId, pageable)
    }
}

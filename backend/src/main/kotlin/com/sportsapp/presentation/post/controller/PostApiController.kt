package com.sportsapp.presentation.post.controller

import com.sportsapp.application.post.usecase.CreatePostUseCase
import com.sportsapp.application.post.usecase.GetPostUseCase
import com.sportsapp.application.post.usecase.SearchPostsUseCase
import com.sportsapp.application.post.dto.PostCriteria
import com.sportsapp.domain.post.vo.PostType
import com.sportsapp.presentation.post.dto.request.CreatePostRequest
import com.sportsapp.presentation.post.dto.response.PostDetailResponse
import com.sportsapp.presentation.post.dto.response.PostResponse
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/posts")
class PostApiController(
    private val searchPostsUseCase: SearchPostsUseCase,
    private val getPostUseCase: GetPostUseCase,
    private val createPostUseCase: CreatePostUseCase,
) {
    @PostMapping
    fun createPost(
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: CreatePostRequest,
    ): ResponseEntity<PostResponse> {
        val post = createPostUseCase.execute(request.toCommand(userId))
        return ResponseEntity.status(201).body(PostResponse.of(post))
    }

    @GetMapping
    fun searchPosts(
        @RequestParam(required = false) type: PostType?,
        @RequestParam(required = false) userId: Long?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Page<PostResponse>> {
        val criteria = PostCriteria(
            type = type,
            userId = userId,
            keyword = keyword,
            page = page,
            size = size,
        )
        return ResponseEntity.ok(searchPostsUseCase.execute(criteria).map { PostResponse.of(it) })
    }

    @GetMapping("/{id}")
    fun getPost(@PathVariable id: Long): ResponseEntity<PostDetailResponse> {
        val (post, comments) = getPostUseCase.execute(id)
        return ResponseEntity.ok(PostDetailResponse.of(post, comments))
    }
}

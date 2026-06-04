package com.sportsapp.presentation.post

import com.sportsapp.application.post.CreatePostUseCase
import com.sportsapp.application.post.GetPostUseCase
import com.sportsapp.application.post.PostCriteria
import com.sportsapp.application.post.PostDetailResponse
import com.sportsapp.application.post.PostResponse
import com.sportsapp.application.post.SearchPostsUseCase
import com.sportsapp.domain.post.PostType
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
        val response = createPostUseCase.execute(request.toCommand(userId))
        return ResponseEntity.status(201).body(response)
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
        return ResponseEntity.ok(searchPostsUseCase.execute(criteria))
    }

    @GetMapping("/{id}")
    fun getPost(@PathVariable id: Long): ResponseEntity<PostDetailResponse> =
        ResponseEntity.ok(getPostUseCase.execute(id))
}

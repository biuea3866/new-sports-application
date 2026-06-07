package com.sportsapp.domain.post.repository

import com.sportsapp.domain.post.dto.PostSearchCriteria
import com.sportsapp.domain.post.entity.Post
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PostCustomRepository {
    fun findByCriteria(criteria: PostSearchCriteria, pageable: Pageable): Page<Post>
}

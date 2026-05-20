package com.sportsapp.domain.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface CustomPostRepository {
    fun findByCriteria(criteria: PostSearchCriteria, pageable: Pageable): Page<Post>
}

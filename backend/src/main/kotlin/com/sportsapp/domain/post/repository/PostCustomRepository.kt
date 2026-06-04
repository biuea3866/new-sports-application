package com.sportsapp.domain.post

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PostCustomRepository {
    fun findByCriteria(criteria: PostSearchCriteria, pageable: Pageable): Page<Post>
}

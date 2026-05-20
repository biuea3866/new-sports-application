package com.sportsapp.infrastructure.persistence.post

import com.querydsl.core.BooleanBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.post.CustomPostRepository
import com.sportsapp.domain.post.Post
import com.sportsapp.domain.post.PostSearchCriteria
import com.sportsapp.domain.post.QPost
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

@Component
class CustomPostRepositoryImpl(
    private val queryFactory: JPAQueryFactory,
) : CustomPostRepository {

    override fun findByCriteria(criteria: PostSearchCriteria, pageable: Pageable): Page<Post> {
        val predicate = buildPredicate(criteria)
        val content = fetchContent(predicate, pageable)
        val total = fetchCount(predicate)
        return PageImpl(content, pageable, total)
    }

    private fun buildPredicate(criteria: PostSearchCriteria): BooleanBuilder {
        val post = QPost.post
        val predicate = BooleanBuilder()
        predicate.and(post.deletedAt.isNull)
        criteria.type?.let { predicate.and(post.type.eq(it)) }
        criteria.userId?.let { predicate.and(post.userId.eq(it)) }
        criteria.keyword?.takeIf { it.isNotBlank() }?.let {
            predicate.and(post.title.containsIgnoreCase(it).or(post.content.containsIgnoreCase(it)))
        }
        return predicate
    }

    private fun fetchContent(predicate: BooleanBuilder, pageable: Pageable): List<Post> {
        val post = QPost.post
        return queryFactory.selectFrom(post)
                           .where(predicate)
                           .orderBy(post.createdAt.desc())
                           .offset(pageable.offset)
                           .limit(pageable.pageSize.toLong())
                           .fetch()
    }

    private fun fetchCount(predicate: BooleanBuilder): Long {
        val post = QPost.post
        return queryFactory.select(post.count())
                           .from(post)
                           .where(predicate)
                           .fetchOne() ?: 0L
    }
}

package com.sportsapp.infrastructure.config

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.beans.factory.annotation.Autowired

/**
 * [U-03] QueryDslConfig — JPAQueryFactory 빈이 EntityManager를 정확히 주입받는다
 */
class QueryDslConfigTest(
    @Autowired private val jpaQueryFactory: JPAQueryFactory,
) : BaseIntegrationTest() {

    init {
        Given("Spring 컨텍스트가 로드된 상태") {
            When("QueryDslConfig가 초기화되면") {
                Then("[U-03] JPAQueryFactory 빈이 null이 아니다") {
                    jpaQueryFactory.shouldNotBeNull()
                }

                Then("[U-03] JPAQueryFactory 타입이다") {
                    jpaQueryFactory.shouldBeInstanceOf<JPAQueryFactory>()
                }
            }
        }
    }
}

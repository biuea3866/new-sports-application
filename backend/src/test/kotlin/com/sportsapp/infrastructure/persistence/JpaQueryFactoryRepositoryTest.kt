package com.sportsapp.infrastructure.persistence

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.BaseIntegrationTest
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.beans.factory.annotation.Autowired

/**
 * [R-02] JPAQueryFactory 빈이 예외 없이 주입되고 정상 동작한다
 *
 * R-03 (금지 어노테이션 사용 시 빌드 실패) 검증은 Gradle harnessCheck 태스크로 수행됩니다.
 * 해당 태스크 실행 시 금지 패턴(no-jpa-query)이 발견되면 빌드가 즉시 실패합니다.
 */
class JpaQueryFactoryRepositoryTest(
    @Autowired private val jpaQueryFactory: JPAQueryFactory,
) : BaseIntegrationTest() {

    init {
        Given("Spring 컨텍스트가 로드된 상태") {
            When("JPAQueryFactory 빈을 주입받으면") {
                Then("[R-02] 빈이 null이 아니며 JPAQueryFactory 인스턴스이다") {
                    jpaQueryFactory.shouldNotBeNull()
                    jpaQueryFactory.shouldBeInstanceOf<JPAQueryFactory>()
                }
            }
        }
    }
}

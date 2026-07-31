package com.sportsapp.application.post.usecase

import com.sportsapp.domain.post.service.PostDomainService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * [W1-01d] `social` 모듈 로컬 경량 컨텍스트 스모크 — 티켓이 요구한 프록시 canary.
 *
 * `kotlin("plugin.spring")`(all-open)이 이 모듈에 적용되지 않으면 Kotlin 클래스는 기본이 final 이라
 * `@Transactional` 이 붙은 [CreatePostUseCase]는 CGLIB 프록시가 생성되지 않는다 — 애플리케이션은
 * 정상 기동하지만 트랜잭션이 조용히 무력화된다. 컴파일·archTest·단위 테스트는 이 실패를 구조적으로
 * 잡지 못한다(비즈니스 로직 자체는 all-open 없이도 동작하기 때문).
 *
 * `social` 은 이 티켓에서 `kotlin("plugin.jpa")` + kapt(QueryDSL)가 **처음 추가**된 모듈이고 254 main
 * 파일·11테이블·대량 `@Transactional` UseCase 를 가진 최대 신규 모듈이다. `commerce` 의
 * `PurchaseLimitedDropUseCaseProxyTest` 가 컨벤션 플러그인의 전역 회귀를 잡아주지만, **social 이
 * 컨벤션 플러그인을 실제로 받았는지·kapt 와의 상호작용으로 all-open 이 깨지지 않았는지는 그 canary 가
 * 증명하지 못한다** — 그래서 모듈 로컬로 하나 더 둔다. **삭제 금지.**
 *
 * `@Configuration(proxyBeanMethods = false)`로 설정 클래스 자체의 CGLIB 향상은 배제해, 이 테스트의
 * 단언이 오직 [CreatePostUseCase]의 all-open 적용 여부만 증명하게 한다.
 */
class CreatePostUseCaseProxyTest : DescribeSpec({

    describe("CreatePostUseCase 빈이 @Transactional 을 가진 채 Spring 컨텍스트에 등록되면") {
        it("all-open 적용으로 CGLIB AOP 프록시가 생성된다") {
            val context = AnnotationConfigApplicationContext(ProxyVerificationConfig::class.java)

            val bean = context.getBean(CreatePostUseCase::class.java)

            AopUtils.isAopProxy(bean) shouldBe true
            AopUtils.isCglibProxy(bean) shouldBe true

            context.close()
        }
    }
}) {
    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    class ProxyVerificationConfig {
        @Bean
        fun postDomainService(): PostDomainService = mockk(relaxed = true)

        @Bean
        fun createPostUseCase(
            postDomainService: PostDomainService,
        ): CreatePostUseCase = CreatePostUseCase(postDomainService)
    }
}

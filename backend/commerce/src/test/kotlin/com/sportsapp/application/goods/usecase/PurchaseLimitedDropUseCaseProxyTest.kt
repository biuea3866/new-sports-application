package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.LimitedDropDomainService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.aop.support.AopUtils
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.EnableTransactionManagement

/**
 * [W1-01b 리뷰 후속 — 회귀 재발 방지] `kotlin("plugin.spring")`(all-open)이 빠지면 Kotlin 클래스는
 * 기본이 final 이라 `@Transactional` 이 붙은 [PurchaseLimitedDropUseCase]는 CGLIB 프록시가 생성되지
 * 않는다 — 애플리케이션은 정상 기동하지만 트랜잭션·`@Retryable` 재시도가 조용히 무력화된다(다른
 * 어떤 테스트로도 이 실패를 잡지 못한다). 실제 Spring 컨텍스트에 빈을 등록해 AOP 프록시로
 * 만들어지는지 직접 단언한다.
 *
 * `@Configuration(proxyBeanMethods = false)`로 설정 클래스 자체의 CGLIB 향상은 배제해,
 * 이 테스트의 단언이 오직 [PurchaseLimitedDropUseCase]의 all-open 적용 여부만 증명하게 한다.
 */
class PurchaseLimitedDropUseCaseProxyTest : DescribeSpec({

    describe("PurchaseLimitedDropUseCase 빈이 @Transactional 을 가진 채 Spring 컨텍스트에 등록되면") {
        it("all-open 적용으로 CGLIB AOP 프록시가 생성된다") {
            val context = AnnotationConfigApplicationContext(ProxyVerificationConfig::class.java)

            val bean = context.getBean(PurchaseLimitedDropUseCase::class.java)

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
        fun limitedDropDomainService(): LimitedDropDomainService = mockk(relaxed = true)

        @Bean
        fun purchaseLimitedDropUseCase(
            limitedDropDomainService: LimitedDropDomainService,
        ): PurchaseLimitedDropUseCase = PurchaseLimitedDropUseCase(limitedDropDomainService)
    }
}

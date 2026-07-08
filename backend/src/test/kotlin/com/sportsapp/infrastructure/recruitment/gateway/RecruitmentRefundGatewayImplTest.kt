package com.sportsapp.infrastructure.recruitment.gateway

import com.sportsapp.domain.recruitment.gateway.RecruitmentRefundGateway
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import org.springframework.context.annotation.AnnotationConfigApplicationContext

class RecruitmentRefundGatewayImplTest : BehaviorSpec({

    Given("prod 프로파일이 활성화된 애플리케이션 컨텍스트") {
        val context = AnnotationConfigApplicationContext()
        context.environment.setActiveProfiles("prod")
        context.register(RecruitmentRefundGatewayImpl::class.java)
        context.refresh()

        Then("RecruitmentRefundGateway 빈이 등록되어 컨텍스트 기동이 실패하지 않는다") {
            val gateway = context.getBean(RecruitmentRefundGateway::class.java)
            gateway.shouldBeInstanceOf<RecruitmentRefundGatewayImpl>()
            context.close()
        }
    }
})

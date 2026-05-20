package com.sportsapp.scenario.payment

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentGateway
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.beans.factory.annotation.Autowired

class PaymentDomainServiceScenarioTest(
    @Autowired private val paymentDomainService: PaymentDomainService,
    @Autowired private val paymentGateway: PaymentGateway,
) : BaseIntegrationTest() {

    init {
        Given("Spring 컨텍스트가 로드된 상태") {
            When("PaymentDomainService 빈을 주입받으면") {
                Then("[S-01] MockPaymentGateway 가 활성화되어 wiring 이 성공한다") {
                    paymentDomainService.shouldNotBeNull()
                    paymentGateway.shouldNotBeNull()
                }
            }
        }
    }
}

package com.sportsapp.application.partner.audit

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class ListPartnerAuditLogsCommandTest : BehaviorSpec({

    Given("from·to를 명시적으로 지정했을 때") {
        val from = ZonedDateTime.now().minusDays(30)
        val to = ZonedDateTime.now()
        val pageable = PageRequest.of(0, 20)

        When("of로 생성하면") {
            val command = ListPartnerAuditLogsCommand.of(
                partnerId = 1L,
                from = from,
                to = to,
                pageable = pageable,
            )

            Then("지정한 값을 그대로 사용한다") {
                command.from shouldBe from
                command.to shouldBe to
            }
        }
    }

    Given("from·to를 지정하지 않았을 때") {
        val pageable = PageRequest.of(0, 20)

        When("of로 생성하면") {
            val command = ListPartnerAuditLogsCommand.of(
                partnerId = 1L,
                from = null,
                to = null,
                pageable = pageable,
            )

            Then("to는 현재 시각 기준, from은 to로부터 7일 전으로 기본 설정된다") {
                val daysBetween = ChronoUnit.DAYS.between(command.from, command.to)
                daysBetween shouldBe 7L
            }
        }
    }
})

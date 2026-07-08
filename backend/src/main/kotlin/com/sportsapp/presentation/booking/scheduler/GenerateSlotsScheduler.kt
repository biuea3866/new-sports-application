package com.sportsapp.presentation.booking.scheduler

import com.sportsapp.application.booking.usecase.GenerateSlotsUseCase
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

private const val AUTOSLOT_ENABLED_PROPERTY = "facility.autoslot.enabled"

/**
 * 시설 운영시간·휴무 기준 향후 N일 예약 가능 슬롯을 매일 자동 생성한다(BE-58, McpAnomalyScheduler 패턴).
 *
 * `facility.autoslot.enabled=false`(기본값)면 빈 자체가 등록되지 않아 대량 슬롯 생성 사고를 막는다
 * (Release Scenario 롤백 지점).
 */
@Component
@ConditionalOnProperty(name = [AUTOSLOT_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class GenerateSlotsScheduler(
    private val generateSlotsUseCase: GenerateSlotsUseCase,
    @Value("\${facility.autoslot.window-days:14}") private val windowDays: Int,
) {
    private val log = LoggerFactory.getLogger(GenerateSlotsScheduler::class.java)

    @Scheduled(cron = "\${facility.autoslot.cron:0 0 1 * * *}")
    fun generateSlots() {
        log.info("event=autoslot-generation-started windowDays={}", windowDays)
        val result = generateSlotsUseCase.execute(windowDays)
        log.info(
            "event=autoslot-generation-completed totalCreated={} failedFacilityCount={}",
            result.totalCreated,
            result.failedFacilityCount,
        )
    }
}

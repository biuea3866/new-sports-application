package com.sportsapp.application.partner.audit

import org.springframework.data.domain.Pageable
import java.time.ZonedDateTime

data class ListPartnerAuditLogsCommand(
    val partnerId: Long,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
    val pageable: Pageable,
) {
    companion object {
        private const val DEFAULT_PERIOD_DAYS = 7L

        fun of(
            partnerId: Long,
            from: ZonedDateTime?,
            to: ZonedDateTime?,
            pageable: Pageable,
        ): ListPartnerAuditLogsCommand {
            val resolvedTo = to ?: ZonedDateTime.now()
            val resolvedFrom = from ?: resolvedTo.minusDays(DEFAULT_PERIOD_DAYS)
            return ListPartnerAuditLogsCommand(
                partnerId = partnerId,
                from = resolvedFrom,
                to = resolvedTo,
                pageable = pageable,
            )
        }
    }
}

package com.sportsapp.domain.featureflag.dto

import org.springframework.data.domain.Pageable

data class GetAuditLogsCommand(
    val key: String,
    val pageable: Pageable,
)

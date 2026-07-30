package com.sportsapp.application.mcp.dto

data class ListMyAnomalyEventsCommand(
    val ownerUserId: Long,
    val page: Int,
    val size: Int,
)

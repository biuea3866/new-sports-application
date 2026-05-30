package com.sportsapp.application.mcp

data class ListMyAnomalyEventsCommand(
    val ownerUserId: Long,
    val page: Int,
    val size: Int,
)

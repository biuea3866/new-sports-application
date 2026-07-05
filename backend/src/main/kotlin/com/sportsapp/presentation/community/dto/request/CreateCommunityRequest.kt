package com.sportsapp.presentation.community.dto.request

import com.sportsapp.application.community.dto.CreateCommunityCommand
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.SportCategory

data class CreateCommunityRequest(
    val name: String,
    val description: String?,
    val visibility: CommunityVisibility,
    val sportCategory: SportCategory,
) {
    fun toCommand(hostUserId: Long): CreateCommunityCommand = CreateCommunityCommand(
        name = name,
        description = description,
        visibility = visibility,
        sportCategory = sportCategory,
        hostUserId = hostUserId,
    )
}

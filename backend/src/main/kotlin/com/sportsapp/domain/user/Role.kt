package com.sportsapp.domain.user

class Role(
    val id: Long,
    val name: String,
    val permissions: List<Permission>,
)

package com.sportsapp.domain.common

enum class UserRoleName {
    USER,
    ADMIN,
    FACILITY_OWNER,
    EVENT_HOST,
    GOODS_SELLER,
    OPERATIONS_MANAGER,
    ;

    companion object {
        fun fromNameOrNull(name: String): UserRoleName? = entries.find { it.name == name }
    }
}

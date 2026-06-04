package com.sportsapp.domain.common

enum class PgEventType(val value: String) {
    PAYMENT_APPROVED("PAYMENT_APPROVED"),
    PAYMENT_CANCELED("PAYMENT_CANCELED"),
    ;

    companion object {
        fun fromValue(value: String): PgEventType =
            entries.find { it.value == value }
                ?: throw IllegalArgumentException("Unknown PgEventType value: $value")

        fun fromValueOrNull(value: String): PgEventType? = entries.find { it.value == value }
    }
}

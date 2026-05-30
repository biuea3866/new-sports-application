package com.sportsapp.domain.payment

enum class PaymentMethod {
    CREDIT_CARD,
    BANK_TRANSFER,
    KAKAO,
    TOSS,
    NAVER,
    DANAL,
}

fun PaymentMethod.toPgProviderName(): String = when (this) {
    PaymentMethod.CREDIT_CARD -> "card"
    PaymentMethod.BANK_TRANSFER -> "bank_transfer"
    PaymentMethod.KAKAO -> "kakao"
    PaymentMethod.TOSS -> "toss"
    PaymentMethod.NAVER -> "naver"
    PaymentMethod.DANAL -> "danal"
}

package com.sportsapp.domain.payment.vo

enum class PaymentMethod {
    CREDIT_CARD,
    BANK_TRANSFER,
    VIRTUAL_ACCOUNT,
    MOBILE_PAY,
    KAKAO,
    TOSS,
    NAVER,
    DANAL,
}

fun PaymentMethod.toPgProviderName(): String = when (this) {
    PaymentMethod.CREDIT_CARD -> "card"
    PaymentMethod.BANK_TRANSFER -> "bank_transfer"
    PaymentMethod.VIRTUAL_ACCOUNT -> "bank_transfer"
    PaymentMethod.MOBILE_PAY -> "card" // mock-pg에 별도 mobile_pay provider 없음 — card로 위임
    PaymentMethod.KAKAO -> "kakao"
    PaymentMethod.TOSS -> "toss"
    PaymentMethod.NAVER -> "naver"
    PaymentMethod.DANAL -> "danal"
}

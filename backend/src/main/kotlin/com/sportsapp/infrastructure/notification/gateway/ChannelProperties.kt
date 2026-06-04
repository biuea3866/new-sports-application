package com.sportsapp.infrastructure.notification

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "external.push")
data class PushProperties(
    val baseUrl: String = "https://exp.host",
    val apiKey: String = "",
)

@ConfigurationProperties(prefix = "external.sms")
data class SmsProperties(
    val baseUrl: String = "http://localhost:9103",
    val apiKey: String = "",
    val from: String = "0700000000",
)

@ConfigurationProperties(prefix = "external.email")
data class EmailProperties(
    val from: String = "no-reply@sportsapp.local",
)

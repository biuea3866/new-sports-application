package com.sportsapp.infrastructure.notification.gateway
import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "notification")
data class NotificationTemplateProperties(
    val templates: Map<String, TemplateDefinition> = emptyMap(),
) {
    data class TemplateDefinition(
        val title: String = "",
        val body: String = "",
    )
}

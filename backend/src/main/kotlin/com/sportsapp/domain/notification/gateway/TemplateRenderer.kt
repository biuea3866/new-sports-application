package com.sportsapp.domain.notification

interface TemplateRenderer {
    fun render(templateId: String, payload: Map<String, Any>): RenderedNotification
}

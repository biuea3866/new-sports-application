package com.sportsapp.domain.notification.gateway
import com.sportsapp.domain.notification.vo.RenderedNotification
interface TemplateRenderer {
    fun render(templateId: String, payload: Map<String, Any>): RenderedNotification
}

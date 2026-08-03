package com.sportsapp.infrastructure.notification.gateway
import com.sportsapp.domain.notification.vo.RenderedNotification
import com.sportsapp.domain.notification.gateway.TemplateRenderer
import com.sportsapp.domain.notification.exception.UnknownTemplateException
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component

@Component
@EnableConfigurationProperties(NotificationTemplateProperties::class)
class TemplateRendererImpl(
    private val properties: NotificationTemplateProperties,
) : TemplateRenderer {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun render(templateId: String, payload: Map<String, Any>): RenderedNotification {
        val definition = properties.templates[templateId]
            ?: throw UnknownTemplateException(templateId)

        return RenderedNotification(
            title = substitute(templateId, definition.title, payload),
            body = substitute(templateId, definition.body, payload),
        )
    }

    private fun substitute(templateId: String, template: String, payload: Map<String, Any>): String {
        val placeholderPattern = Regex("""\{(\w+)\}""")
        return placeholderPattern.replace(template) { match ->
            val key = match.groupValues[1]
            val value = payload[key]
            if (value == null) {
                log.warn("[TemplateRenderer] templateId={} missing placeholder key={}", templateId, key)
                ""
            } else {
                value.toString()
            }
        }
    }
}

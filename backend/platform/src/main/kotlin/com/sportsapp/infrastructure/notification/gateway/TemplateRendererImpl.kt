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

    /**
     * 치환 후 공백을 정규화한다 — 값이 없는 placeholder 가 빈 문자열로 치환되면
     * "{facilityName} 예약이 확정되었습니다." 가 " 예약이…" 처럼 앞 공백·이중 공백을 남긴다.
     */
    private fun substitute(templateId: String, template: String, payload: Map<String, Any>): String {
        val placeholderPattern = Regex("""\{(\w+)\}""")
        val substituted = placeholderPattern.replace(template) { match ->
            val key = match.groupValues[1]
            val value = payload[key]
            if (value == null) {
                log.warn("[TemplateRenderer] templateId={} missing placeholder key={}", templateId, key)
                ""
            } else {
                value.toString()
            }
        }
        return substituted.replace(Regex("""[ \t]{2,}"""), " ").trim()
    }
}

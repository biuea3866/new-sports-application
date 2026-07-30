package com.sportsapp.infrastructure.config

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import org.slf4j.Logger
import java.io.File

/**
 * logback-spring.xml(BE-04) 이 유효하게 파싱되고, 기존 콘솔 출력을 보존한 채
 * OTLP appender(OpenTelemetryAppender)·trace_id/span_id MDC 패턴을 포함하는지 검증한다.
 * 실제 OpenTelemetry SDK install 없이도 JoranConfigurator 파싱 자체는 예외 없이 끝나야 한다
 * (OTLP 엔드포인트 미설정 시에도 콘솔 로그가 정상 출력돼야 한다는 요구사항의 전제).
 */
class LogbackSpringConfigTest : BehaviorSpec({

    val logbackConfigFile = File("src/main/resources/logback-spring.xml")

    Given("logback-spring.xml 을 JoranConfigurator 로 파싱하면") {
        val loggerContext = LoggerContext()
        val configurator = JoranConfigurator()
        configurator.context = loggerContext
        configurator.doConfigure(logbackConfigFile)

        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

        When("CONSOLE appender 를 조회하면") {
            val consoleAppender = rootLogger.getAppender("CONSOLE")

            Then("예외 없이 로드되고 콘솔 appender 가 root logger 에 등록된다") {
                consoleAppender.shouldNotBeNull()
            }
        }

        When("OTLP appender 를 조회하면") {
            val otlpAppender = rootLogger.getAppender("OTLP")

            Then("OpenTelemetryAppender 가 root logger 에 등록된다") {
                otlpAppender.shouldNotBeNull()
                otlpAppender.javaClass.name shouldContain "OpenTelemetryAppender"
            }
        }
    }

    Given("logback-spring.xml 원문을 확인하면") {
        val xmlContent = logbackConfigFile.readText()

        Then("콘솔 로그 패턴에 Micrometer MDC 키(traceId/spanId) 참조가 포함된다") {
            xmlContent shouldContain "%X{traceId"
            xmlContent shouldContain "%X{spanId"
        }

        Then("OpenTelemetryAppender 클래스를 참조한다") {
            xmlContent shouldContain "io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender"
        }
    }
})

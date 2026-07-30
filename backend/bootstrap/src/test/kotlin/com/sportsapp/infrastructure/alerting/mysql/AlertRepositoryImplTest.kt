package com.sportsapp.infrastructure.alerting.mysql

import com.sportsapp.SharedTestContainers
import com.sportsapp.domain.alerting.entity.AlertStatus
import com.sportsapp.domain.alerting.repository.AlertRepository
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSignal
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.domain.alerting.vo.TelemetrySnapshot
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.infrastructure.audit.SecurityAuditorAware
import com.sportsapp.infrastructure.audit.ZonedDateTimeProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.domain.AuditorAware
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container

/**
 * [AlertRepositoryImpl] 슬라이스 통합 테스트.
 *
 * alerting 도메인의 다른 구현체(TelemetryQueryGateway)가 아직 없어
 * [AlertDomainService] 빈 생성이 실패하므로 전체 `@SpringBootTest`(BaseJpaIntegrationTest)를 쓰지 않는다.
 * `@DataJpaTest`로 JPA 관련 auto-configuration(Flyway 포함)만 올리고, `SportsApplication`의
 * `@EnableJpaAuditing(auditorAwareRef = "securityAuditorAware", dateTimeProviderRef = "zonedDateTimeProvider")`가
 * 참조하는 두 빈 이름을 [AlertRepositoryImplTestConfig]에서 `@Bean` 메서드명으로 명시 노출하고,
 * 검증 대상(AlertRepositoryImpl)만 `@Import`한다.
 *
 * `@DataJpaTest`는 기본적으로 `com.sportsapp` 전체 패키지의 Entity·Spring Data Repository를 스캔한다.
 * 다른 도메인의 QueryDSL 커스텀 Repository(예: `OperatorInboxNotificationQueryDslRepositoryImpl`)가
 * `JPAQueryFactory` 빈을 요구해 컨텍스트 기동이 깨지므로, `@EnableJpaRepositories`·`@EntityScan`으로
 * alerting 패키지만 스캔 범위를 좁힌다(Flyway는 스캔 범위와 무관하게 전체 마이그레이션을 그대로 적용한다).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnableJpaRepositories(basePackageClasses = [AlertJpaRepository::class])
@EntityScan(basePackageClasses = [Alert::class])
@Import(AlertRepositoryImpl::class, AlertCustomRepositoryImpl::class, AlertRepositoryImplTestConfig::class)
class AlertRepositoryImplTest(
    @Autowired private val alertRepository: AlertRepository,
) : BehaviorSpec() {

    companion object {
        @JvmField
        @Container
        @ServiceConnection
        val mysqlContainer: MySQLContainer<*> = SharedTestContainers.mysql
    }

    init {
        Given("신규 발생한 Alert(RAISED)") {
            val signal = AlertSignal(
                endpoint = "/api/v1/bookings",
                source = AlertSource.LATENCY,
                severity = AlertSeverity.WARN,
            )
            val alert = Alert.create(signal, env = "test")

            When("save 후 findById로 조회하면") {
                val saved = alertRepository.save(alert)
                val found = alertRepository.findById(saved.id)

                Then("source·severity·env·status가 일치한다") {
                    found.shouldNotBeNull()
                    found.endpoint shouldBe "/api/v1/bookings"
                    found.source shouldBe AlertSource.LATENCY
                    found.severity shouldBe AlertSeverity.WARN
                    found.env shouldBe "test"
                    found.currentStatus shouldBe AlertStatus.RAISED
                }
            }
        }

        Given("원지표(TelemetrySnapshot)가 부착된 Alert") {
            val signal = AlertSignal(
                endpoint = "/api/v1/goods",
                source = AlertSource.OVERSELL,
                severity = AlertSeverity.CRITICAL,
            )
            val alert = Alert.create(signal, env = "prod")
            val snapshot = TelemetrySnapshot(
                metricsSummary = "oversell_count=3",
                logSamples = listOf("stock decrement race detected"),
                traceSamples = listOf("traceId=xyz"),
            )
            alert.attachTelemetry(snapshot)

            When("저장·조회를 왕복하면") {
                val saved = alertRepository.save(alert)
                val found = requireNotNull(alertRepository.findById(saved.id))

                Then("telemetry 필드가 손실 없이 복원된다") {
                    found.currentTelemetry.shouldNotBeNull()
                    found.currentTelemetry shouldBe snapshot
                    found.currentStatus shouldBe AlertStatus.ENRICHED
                }
            }
        }

        Given("존재하지 않는 alert id") {
            val nonExistentId = Long.MAX_VALUE - 1

            When("findById를 호출하면") {
                val found = alertRepository.findById(nonExistentId)

                Then("null을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("ENRICHED 상태로 저장된 Alert") {
            val signal = AlertSignal(
                endpoint = "/api/v1/deploy",
                source = AlertSource.DEPLOYMENT,
                severity = AlertSeverity.INFO,
            )
            val alert = Alert.create(signal, env = "dev")
            alert.attachTelemetry(
                TelemetrySnapshot(
                    metricsSummary = "rollout_delay_seconds=120",
                    logSamples = emptyList(),
                    traceSamples = emptyList(),
                )
            )
            val saved = alertRepository.save(alert)

            When("markDelivered 후 재저장하면") {
                val toDeliver = requireNotNull(alertRepository.findById(saved.id))
                toDeliver.markDelivered()
                alertRepository.save(toDeliver)
                val redelivered = requireNotNull(alertRepository.findById(saved.id))

                Then("DELIVERED·delivered_at이 반영된다") {
                    redelivered.currentStatus shouldBe AlertStatus.DELIVERED
                    redelivered.deliveredAtValue.shouldNotBeNull()
                }
            }
        }

        Given("보존 기간(90일)이 지난 Alert와 최근 발생한 Alert가 함께 존재하는 상태") {
            val expiredSignal = AlertSignal(
                endpoint = "/api/v1/expired",
                source = AlertSource.LATENCY,
                severity = AlertSeverity.WARN,
            )
            val expiredAlert = Alert.reconstitute(
                signalKey = expiredSignal.cooldownKey("test"),
                endpoint = expiredSignal.endpoint,
                source = expiredSignal.source,
                severity = expiredSignal.severity,
                env = "test",
                status = AlertStatus.RAISED,
                telemetry = null,
                raisedAt = ZonedDateTime.now().minusDays(91),
                deliveredAt = null,
            )
            val recentSignal = AlertSignal(
                endpoint = "/api/v1/recent",
                source = AlertSource.LATENCY,
                severity = AlertSeverity.WARN,
            )
            val recentAlert = Alert.create(recentSignal, env = "test")
            val expiredSaved = alertRepository.save(expiredAlert)
            val recentSaved = alertRepository.save(recentAlert)

            When("deleteRaisedBefore(현재로부터 90일 전)를 호출하면") {
                val deletedCount = alertRepository.deleteRaisedBefore(ZonedDateTime.now().minusDays(90))

                Then("만료된 Alert만 삭제되고 최근 Alert는 남는다") {
                    deletedCount shouldBe 1L
                    alertRepository.findById(expiredSaved.id).shouldBeNull()
                    alertRepository.findById(recentSaved.id).shouldNotBeNull()
                }
            }
        }
    }
}

/**
 * `@DataJpaTest` 슬라이스는 [SecurityAuditorAware]·[ZonedDateTimeProvider]를 일반 `@Component`
 * 스캔으로 올리지 않으므로, `@EnableJpaAuditing`이 참조하는 빈 이름(`securityAuditorAware`,
 * `zonedDateTimeProvider`)을 `@Bean` 메서드명으로 명시 노출한다.
 */
@TestConfiguration
class AlertRepositoryImplTestConfig {

    @Bean
    fun securityAuditorAware(): AuditorAware<Long> = SecurityAuditorAware()

    @Bean
    fun zonedDateTimeProvider(): DateTimeProvider = ZonedDateTimeProvider()
}

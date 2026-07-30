package com.sportsapp.infrastructure.config

import com.sportsapp.application.goods.usecase.PurchaseLimitedDropUseCase
import com.sportsapp.infrastructure.goods.retry.LimitedDropRetryProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.springframework.retry.annotation.Retryable
import org.yaml.snakeyaml.Yaml
import java.io.File
import kotlin.math.ceil

/**
 * [FIX-03] `infra/nginx/lb.conf` 의 프록시 타임아웃이 "백엔드가 먼저 실패 응답을 내고,
 * LB는 그 응답을 전달한다" 순서를 보장하는지 검증한다 (근거: 실측 리포트 L64-69, 5xx 3,972건 —
 * nginx read/send timeout(30s)이 HikariCP 풀 대기(30s)와 사실상 같아 nginx가 먼저 포기했다).
 *
 * Spring 컨텍스트 없이 파일 텍스트만 정규식으로 파싱한다 — nginx 는 JVM 밖 별도 프로세스라
 * 컨텍스트 부팅이 애초에 무의미하다.
 *
 * [code-review p2] "FIX-02(재시도 예산 200회→20회 축소) 선행 필수" — 이 테스트가 검증하는
 * proxy_read_timeout=15s는 "백엔드 최대 요청 수명 = 풀 대기(connection-timeout, 5s) + 재시도
 * 예산" 을 전제로 세운 값이다. FIX-02(재시도 예산 200회→20회 축소, `LimitedDropRetryProperties`)가
 * 이 브랜치의 base(`origin/main`)에 먼저 머지돼 이 전제가 성립한다 — 아래 "완전한 불변식" 절이
 * `LimitedDropRetryProperties`·`PurchaseLimitedDropUseCase`의 `@Retryable` 실제 값으로
 * 재시도 예산 상한까지 반영해 검증한다 (하드코딩 금지 — 값이 드리프트하면 이 테스트가 깨진다).
 */
class NginxLbTimeoutConfigTest : BehaviorSpec({

    fun readLbConf(): String = File("../infra/nginx/lb.conf").readText()

    fun secondsOf(conf: String, directive: String): Int {
        val match = Regex("""$directive\s+(\d+)s;""").find(conf)
            ?: error("$directive 지시어를 lb.conf 에서 찾을 수 없습니다")
        return match.groupValues[1].toInt()
    }

    /**
     * [code-review p2] `hikariConnectionTimeoutSeconds = 5` 하드코딩을 제거하고
     * application.yml의 실제 값을 읽는다 — 하드코딩이면 yml 값이 드리프트해도 이 테스트가
     * 못 잡는다. `spring.datasource.hikari.connection-timeout`은
     * `${DB_HIKARI_CONNECTION_TIMEOUT:5000}` 형태(Spring 프로퍼티 플레이스홀더 + 기본값)로
     * 저장돼 있어, YAML 파싱 후 플레이스홀더의 기본값(ms)을 정규식으로 추출해 초 단위로 변환한다.
     */
    @Suppress("UNCHECKED_CAST")
    fun hikariConnectionTimeoutSecondsFromApplicationYml(): Int {
        val yamlText = File("src/main/resources/application.yml").readText()
        val parsed = Yaml().load<Map<String, Any?>>(yamlText)
        val spring = parsed["spring"] as? Map<String, Any?>
            ?: error("application.yml 에 spring 섹션이 없습니다")
        val datasource = spring["datasource"] as? Map<String, Any?>
            ?: error("application.yml 에 spring.datasource 섹션이 없습니다")
        val hikari = datasource["hikari"] as? Map<String, Any?>
            ?: error("application.yml 에 spring.datasource.hikari 섹션이 없습니다")
        val rawValue = hikari["connection-timeout"]?.toString()
            ?: error("application.yml 에 spring.datasource.hikari.connection-timeout 이 없습니다")
        val defaultMillis = Regex("""\$\{[A-Z_]+:(\d+)}""").find(rawValue)?.groupValues?.get(1)?.toInt()
            ?: rawValue.toInt()
        return defaultMillis / 1000
    }

    /**
     * [code-review p2 후속] `PurchaseLimitedDropUseCase.execute()`의 `@Retryable` 실제 설정
     * (backoff maxDelay)과 `LimitedDropRetryProperties`의 실제 기본 `maxAttempts`로 재시도
     * 예산 상한(밀리초)을 계산한다 — 숫자를 이 테스트에 하드코딩하지 않는다.
     *
     * Spring Retry `ExponentialRandomBackOffPolicy`(backoff `random = true`)는 각 재시도
     * 간격에 지터(jitter)를 곱하되 `getSleepAndIncrement()`에서 `maxInterval`(=Backoff.maxDelay)로
     * 상한을 고정한다(spring-retry 소스 확인) — 즉 지터를 포함해도 재시도 1회당 지연은
     * `maxDelay`를 넘지 않는다. 따라서 최악의 재시도 예산 상한은
     * `(maxAttempts - 1) * maxDelay`(최초 시도는 지연 없이 즉시 실행되므로 재시도 횟수는
     * maxAttempts - 1)다.
     */
    fun retryBudgetUpperBoundMillisFromUseCase(): Long {
        val executeMethod = PurchaseLimitedDropUseCase::class.java.methods.firstOrNull { it.name == "execute" }
            ?: error("PurchaseLimitedDropUseCase 에 execute 메서드가 없습니다")
        val retryable = executeMethod.getAnnotation(Retryable::class.java)
            ?: error("PurchaseLimitedDropUseCase.execute 에 @Retryable 이 없습니다 — 재시도 예산 전제가 깨졌습니다")
        val maxAttempts = LimitedDropRetryProperties().maxAttempts
        val maxDelayMillis = retryable.backoff.maxDelay
        return (maxAttempts - 1).toLong() * maxDelayMillis
    }

    Given("infra/nginx/lb.conf 의 location / 블록") {
        val conf = readLbConf()

        When("proxy_read_timeout, proxy_send_timeout 을 확인하면") {
            Then("둘 다 15초다 (구 30초 — HikariCP 풀 대기 5초와의 2배 이상 여유)") {
                secondsOf(conf, "proxy_read_timeout") shouldBe 15
                secondsOf(conf, "proxy_send_timeout") shouldBe 15
            }
        }

        When("HikariCP connection-timeout(application.yml 실제 값)과 비교하면") {
            Then("proxy_read_timeout 이 백엔드 커넥션 대기 시간의 2배 이상이다 (설정 정합 검증)") {
                val hikariConnectionTimeoutSeconds = hikariConnectionTimeoutSecondsFromApplicationYml()
                secondsOf(conf, "proxy_read_timeout") shouldBeGreaterThanOrEqual hikariConnectionTimeoutSeconds * 2
            }
        }

        When("HikariCP connection-timeout + PurchaseLimitedDropUseCase 재시도 예산 상한(둘 다 실제 값)을 합산하면") {
            Then("proxy_read_timeout 이 백엔드 최대 요청 수명(풀 대기 + 재시도 예산)보다 크다 (완전한 불변식)") {
                val hikariConnectionTimeoutSeconds = hikariConnectionTimeoutSecondsFromApplicationYml()
                val retryBudgetUpperBoundSeconds =
                    ceil(retryBudgetUpperBoundMillisFromUseCase() / 1000.0).toInt()
                val backendMaxRequestLifetimeSeconds = hikariConnectionTimeoutSeconds + retryBudgetUpperBoundSeconds

                secondsOf(conf, "proxy_read_timeout") shouldBeGreaterThan backendMaxRequestLifetimeSeconds
            }
        }

        When("proxy_connect_timeout 을 확인하면") {
            Then("5초로 그대로 유지된다 (변경 대상 아님)") {
                secondsOf(conf, "proxy_connect_timeout") shouldBe 5
            }
        }

        When("proxy_next_upstream 설정을 확인하면") {
            Then("error timeout http_502 http_503 재시도 정책이 그대로 유지된다 (변경 대상 아님)") {
                conf.contains("proxy_next_upstream error timeout http_502 http_503;") shouldBe true
                conf.contains("proxy_next_upstream_tries 3;") shouldBe true
            }
        }
    }
})

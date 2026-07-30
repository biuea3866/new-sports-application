package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml
import java.io.File

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
 * 예산" 을 전제로 세운 값이다. 이 브랜치의 base 시점(`PurchaseLimitedDropUseCase`)에는 아직
 * FIX-02가 반영되지 않아 재시도 예산이 과거 설정(maxAttempts=200, Backoff 누적 약 19~20초)
 * 그대로다 — 이 상태로 단독 머지하면 백엔드 최대 수명(약 25s) > LB 15s가 되어 이 테스트가
 * 세우는 불변식이 실제로는 깨진 채로 그린이 된다. FIX-02가 먼저 머지돼 재시도 예산이 축소된
 * 뒤에만 이 15s 값의 전제가 성립한다. FIX-02 머지 후 `LimitedDropRetryProperties`의 실제
 * 재시도 예산까지 반영한 완전한 불변식(LB 타임아웃 > 풀 대기 + 재시도 예산) 검증을 추가한다.
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

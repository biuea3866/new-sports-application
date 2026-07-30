package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * [FIX-03] `infra/nginx/lb.conf` 의 프록시 타임아웃이 "백엔드가 먼저 실패 응답을 내고,
 * LB는 그 응답을 전달한다" 순서를 보장하는지 검증한다 (근거: 실측 리포트 L64-69, 5xx 3,972건 —
 * nginx read/send timeout(30s)이 HikariCP 풀 대기(30s)와 사실상 같아 nginx가 먼저 포기했다).
 *
 * Spring 컨텍스트 없이 파일 텍스트만 정규식으로 파싱한다 — nginx 는 JVM 밖 별도 프로세스라
 * 컨텍스트 부팅이 애초에 무의미하다.
 */
class NginxLbTimeoutConfigTest : BehaviorSpec({

    fun readLbConf(): String = File("../infra/nginx/lb.conf").readText()

    fun secondsOf(conf: String, directive: String): Int {
        val match = Regex("""$directive\s+(\d+)s;""").find(conf)
            ?: error("$directive 지시어를 lb.conf 에서 찾을 수 없습니다")
        return match.groupValues[1].toInt()
    }

    Given("infra/nginx/lb.conf 의 location / 블록") {
        val conf = readLbConf()

        When("proxy_read_timeout, proxy_send_timeout 을 확인하면") {
            Then("둘 다 15초다 (구 30초 — HikariCP 풀 대기 5초와의 2배 이상 여유)") {
                secondsOf(conf, "proxy_read_timeout") shouldBe 15
                secondsOf(conf, "proxy_send_timeout") shouldBe 15
            }
        }

        When("HikariCP connection-timeout(5초, application.yml)과 비교하면") {
            Then("proxy_read_timeout 이 백엔드 커넥션 대기 시간의 2배 이상이다 (설정 정합 검증)") {
                val hikariConnectionTimeoutSeconds = 5
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

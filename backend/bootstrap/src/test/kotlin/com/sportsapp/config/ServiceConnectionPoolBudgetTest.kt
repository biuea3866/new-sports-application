package com.sportsapp.config

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.yaml.snakeyaml.Yaml

/**
 * W1-03 — 서비스별 HikariCP 커넥션 예산이 설계(실행설계 §7-6)와 일치하는지 고정한다.
 *
 * 2단계에서 각 서비스가 자기 프로파일로 뜨면 이 값들이 실제 커넥션 수가 된다. 합계가
 * `max_connections`(W1-02가 500으로 상향)를 넘으면 기동 시점이 아니라 **부하가 오를 때**
 * 커넥션 획득 실패로 터지므로, 값을 코드로 고정해 조용한 드리프트를 막는다.
 *
 * 1단계에서는 단일 `bootstrap` 앱만 뜨므로 이 프로파일들은 활성화되지 않는다 — 이 테스트는
 * "2단계 활성화 대상 설정이 확정돼 있다"를 검증한다.
 */
class ServiceConnectionPoolBudgetTest : DescribeSpec({

    val mysqlMaxConnections = 500

    /**
     * 실행설계 §7-6 커넥션 예산 표. 구현이 이 표를 벗어나면 실패한다.
     *
     * `replicas`는 §7-6 목표 구성(10 인스턴스) 기준이며, 예산 상한 검증에만 쓴다.
     */
    data class ServiceBudget(val profile: String, val poolPerInstance: Int, val replicas: Int)

    val budgets = listOf(
        ServiceBudget(profile = "commerce", poolPerInstance = 40, replicas = 2),
        ServiceBudget(profile = "facility-booking", poolPerInstance = 30, replicas = 2),
        ServiceBudget(profile = "payment", poolPerInstance = 20, replicas = 2),
        ServiceBudget(profile = "social", poolPerInstance = 25, replicas = 2),
        ServiceBudget(profile = "platform", poolPerInstance = 20, replicas = 1),
    )

    fun loadProfileYaml(profile: String): Map<String, Any>? =
        Thread.currentThread().contextClassLoader
            .getResourceAsStream("application-$profile.yml")
            ?.use { inputStream -> Yaml().load<Map<String, Any>>(inputStream) }

    fun hikariOf(yaml: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val spring = yaml["spring"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val datasource = spring["datasource"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        return datasource["hikari"] as Map<String, Any>
    }

    describe("DB를 쓰는 서비스 프로파일") {
        budgets.forEach { budget ->
            it("${budget.profile} 은 풀 ${budget.poolPerInstance}, connection-timeout 5초를 갖는다") {
                val yaml = loadProfileYaml(budget.profile)
                yaml.shouldNotBeNull()

                val hikari = hikariOf(yaml)
                hikari["maximum-pool-size"] shouldBe budget.poolPerInstance
                // FIX-03 확정값 — nginx read/send timeout(15s)보다 먼저 실패 응답을 내보내야 한다.
                hikari["connection-timeout"] shouldBe 5000
            }
        }
    }

    describe("edge 프로파일") {
        it("DB 권한이 0(W1-04 GRANT)이므로 데이터소스 자동 구성을 제외한다") {
            val yaml = loadProfileYaml("edge")
            yaml.shouldNotBeNull()

            @Suppress("UNCHECKED_CAST")
            val spring = yaml["spring"] as Map<String, Any>
            val excluded = (spring["autoconfigure"] as Map<*, *>)["exclude"] as List<*>

            excluded shouldContainAll listOf(
                "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
                "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
                "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            )
        }

        it("데이터소스 설정 자체를 갖지 않는다 — 풀 크기 0을 적는 것으로 끝내지 않는다") {
            val yaml = loadProfileYaml("edge")
            yaml.shouldNotBeNull()

            @Suppress("UNCHECKED_CAST")
            val spring = yaml["spring"] as Map<String, Any>
            spring["datasource"] shouldBe null
        }
    }

    describe("커넥션 예산 합계") {
        it("목표 구성(§7-6)의 합계가 max_connections $mysqlMaxConnections 을 넘지 않는다") {
            val target = budgets.sumOf { it.poolPerInstance * it.replicas }

            target shouldBe 250
            target shouldBeLessThanOrEqual mysqlMaxConnections
        }

        it("최소 구성(전 서비스 1 replica)의 합계가 max_connections 를 넘지 않는다") {
            val minimal = budgets.sumOf { it.poolPerInstance }

            minimal shouldBe 135
            minimal shouldBeLessThanOrEqual mysqlMaxConnections
        }
    }
})

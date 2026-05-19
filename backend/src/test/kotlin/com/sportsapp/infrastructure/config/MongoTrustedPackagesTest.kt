package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain

/**
 * [U-02] MongoConfig의 mapping base packages가 도메인 패키지만 포함하고,
 * 외부 DTO 패키지(e.g. infrastructure, presentation)는 포함하지 않는다
 */
class MongoTrustedPackagesTest : BehaviorSpec({

    val mongoConfig = MongoConfig()

    Given("MongoConfig가 초기화된 상태") {
        val basePackages = mongoConfig.domainMappingPackages()

        When("mapping base packages를 조회하면") {
            Then("[U-02] 도메인 패키지가 포함된다") {
                basePackages shouldContain "com.sportsapp.domain"
            }

            Then("[U-02] infrastructure 패키지는 포함되지 않는다") {
                basePackages shouldNotContain "com.sportsapp.infrastructure"
            }

            Then("[U-02] presentation 패키지는 포함되지 않는다") {
                basePackages shouldNotContain "com.sportsapp.presentation"
            }

            Then("[U-02] application 패키지는 포함되지 않는다") {
                basePackages shouldNotContain "com.sportsapp.application"
            }
        }
    }
})

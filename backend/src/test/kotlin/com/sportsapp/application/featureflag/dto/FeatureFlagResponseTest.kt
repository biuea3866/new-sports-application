package com.sportsapp.application.featureflag.dto

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.strategy.Variant
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class FeatureFlagResponseTest : BehaviorSpec({

    val objectMapper: ObjectMapper = ObjectMapper().registerKotlinModule().registerModule(JavaTimeModule())

    Given("GlobalToggle 전략을 가진 FeatureFlag로 응답을 생성하면") {
        val flag = testFeatureFlag(
            flagKey = "demo.feature.hello",
            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
        )
        val response = FeatureFlagResponse.of(flag)

        Then("id·key·type·status·description·시각 필드가 엔티티 값 그대로 매핑된다") {
            response.id shouldBe flag.id
            response.key shouldBe flag.flagKey
            response.type shouldBe flag.type
            response.status shouldBe flag.status
            response.description shouldBe flag.description
            response.createdAt shouldBe flag.createdAt
            response.updatedAt shouldBe flag.updatedAt
        }
    }

    Given("VariantBucketing 전략을 가진 FeatureFlag로 응답을 생성하면") {
        val variants = listOf(Variant(name = "control", weight = 50), Variant(name = "treatment", weight = 50))
        val flag = testFeatureFlag(
            flagKey = "demo.feature.experiment",
            type = FeatureFlagType.EXPERIMENT,
            strategy = EvaluationStrategy.VariantBucketing(variants = variants),
        )
        val response = FeatureFlagResponse.of(flag)

        Then("strategy 필드에 VariantBucketing이 그대로 담긴다") {
            response.strategy shouldBe EvaluationStrategy.VariantBucketing(variants = variants)
        }

        When("JSON으로 직렬화하면") {
            val json = objectMapper.readTree(objectMapper.writeValueAsString(response))

            Then("strategyType 판별자와 variants 배열이 함께 직렬화된다") {
                json["strategy"]["strategyType"].asText() shouldBe "VARIANT_BUCKETING"
                json["strategy"]["variants"].size() shouldBe 2
                json["strategy"]["variants"][0]["name"].asText() shouldBe "control"
                json["strategy"]["variants"][0]["weight"].asInt() shouldBe 50
            }
        }
    }

    Given("GlobalToggle 전략을 가진 FeatureFlag로 응답을 JSON 직렬화하면") {
        val flag = testFeatureFlag(strategy = EvaluationStrategy.GlobalToggle(enabled = false))
        val response = FeatureFlagResponse.of(flag)
        val json = objectMapper.readTree(objectMapper.writeValueAsString(response))

        Then("strategyType이 GLOBAL_TOGGLE로 판별된다") {
            json["strategy"]["strategyType"].asText() shouldBe "GLOBAL_TOGGLE"
            json["strategy"]["enabled"].asBoolean() shouldBe false
        }
    }
})

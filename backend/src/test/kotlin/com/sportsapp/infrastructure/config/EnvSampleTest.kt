package com.sportsapp.infrastructure.config

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * 레포 루트 `.env.sample` 이 실제 키 값 없이 placeholder 만 나열하는지 검증한다
 * (PRD NFR: 평문 노출 0).
 */
class EnvSampleTest : BehaviorSpec({

    // backend 모듈 working dir 기준 레포 루트로 한 단계 올라간다.
    val envSampleFile = File("../.env.sample")

    val requiredKeys = listOf(
        "KAKAO_REST_API_KEY",
        "DATA_GO_KR_SERVICE_KEY",
        "SOLAPI_API_KEY",
        "EXTERNAL_GEOCODING_BASE_URL",
        "EXTERNAL_PUBLIC_FACILITY_BASE_URL",
        "EXTERNAL_WEATHER_BASE_URL",
        "EXTERNAL_SMS_BASE_URL",
    )

    Given("레포 루트 .env.sample 파일") {
        When("파일 존재 여부를 확인하면") {
            Then("파일이 존재한다") {
                envSampleFile.exists() shouldBe true
            }
        }

        val lines = envSampleFile.readLines()
        val keyValueLines = lines
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }

        When("전환 대상 env 키 목록을 확인하면") {
            Then("필수 키가 모두 나열된다") {
                requiredKeys.forEach { key ->
                    keyValueLines.map { it.substringBefore("=") } shouldContain key
                }
            }
        }

        When("각 키의 값을 확인하면") {
            Then("실제 키 값 없이 빈 placeholder 만 존재한다") {
                keyValueLines.forEach { line ->
                    val value = line.substringAfter("=", missingDelimiterValue = "NOT_KEY_VALUE_LINE")
                    value shouldBe ""
                }
            }
        }
    }
})

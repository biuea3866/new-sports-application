rootProject.name = "sports-application"

pluginManagement {
    val springBootVersion: String by settings
    val kotlinVersion: String by settings

    plugins {
        id("org.springframework.boot") version springBootVersion
        id("io.spring.dependency-management") version "1.1.6"
        kotlin("jvm") version kotlinVersion
        kotlin("plugin.spring") version kotlinVersion
        kotlin("plugin.jpa") version kotlinVersion
        kotlin("kapt") version kotlinVersion
        id("io.gitlab.arturbosch.detekt") version "1.23.6"
        id("org.jetbrains.kotlinx.kover") version "0.8.3"
    }
}

// W1-01a — 멀티모듈 골격. 위상 순서(실측 교차 import 78파일 기준):
// common -> payment -> {commerce, facility-booking} -> platform -> social -> edge -> bootstrap
// 이 티켓은 골격 + common 추출 + bootstrap 수용만 담당한다. 컨텍스트 소스 이관은 W1-01b/c/d.
include(":common")
include(":payment")
include(":commerce")
include(":facility-booking")
include(":platform")
include(":social")
include(":edge")
include(":bootstrap")

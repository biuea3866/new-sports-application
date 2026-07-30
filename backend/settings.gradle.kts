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
        // kover는 origin/main(단일 모듈)에서 루트에 적용돼 있었으나, 8모듈 분리 이후 "루트가 아니라
        // 각 모듈에 적용 + 커버리지 리포트를 어떻게 집계할지"는 이 티켓(골격 추출)이 아니라 별도 설계가
        // 필요한 결정이라 의도적으로 미적용 상태로 남긴다. pluginManagement 선언만 유지해 버전을
        // 고정해두고, 실제 적용은 후속 티켓에서 모듈별 적용 범위·집계 리포트 전략과 함께 결정한다.
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

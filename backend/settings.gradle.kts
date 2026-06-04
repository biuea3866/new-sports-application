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

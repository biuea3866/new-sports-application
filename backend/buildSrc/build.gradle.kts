plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    // 버전은 backend/gradle.properties(kotlinVersion) 및 root build.gradle.kts(detekt)와 동일하게 고정.
    // buildSrc 는 별도 빌드라 루트 gradle.properties 의 pluginManagement 를 상속하지 않으므로 직접 명시한다.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.23")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
}

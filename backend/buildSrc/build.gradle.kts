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
    // [W1-01b 리뷰 후속] kotlin("plugin.spring")(all-open)을 컨벤션 플러그인에서 적용하려면 그 구현체인
    // kotlin-allopen 이 buildSrc 자신의 classpath 에 있어야 한다(정밀 컴파일 스크립트 플러그인은
    // 루트 pluginManagement 를 상속하지 않는다 — kotlin-gradle-plugin 만으로는 plugin.spring 마커를
    // 해석하지 못한다).
    implementation("org.jetbrains.kotlin:kotlin-allopen:1.9.23")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.6")
}

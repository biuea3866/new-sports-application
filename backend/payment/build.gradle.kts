// [W1-01a] payment — 아웃바운드 동기 의존 0 (§1-2 실측).
// [W1-01b] payment 컨텍스트 소스(37 main) 이관 + 이 모듈이 실제로 쓰는 기술 의존만 선언한다.
// common 과 동일하게 io.spring.dependency-management DSL 대신 Gradle 네이티브 platform() 을 쓴다
// (common/build.gradle.kts 상단 주석 근거 — kotlin-stdlib 버전 강제 승격으로 인한 detekt/kapt 충돌 회피).
plugins {
    id("sportsapp.kotlin-conventions")
    kotlin("plugin.jpa")
    kotlin("kapt")
}

val springBootVersion: String by project

dependencies {
    implementation(project(":common"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // QueryDSL — payment 가 @Entity(Payment 등) 를 소유하는 첫 리프 모듈. common 과 동일 패턴으로
    // 이 모듈 자신의 kapt 실행에서 Q타입을 생성한다 (implementation 의존은 하위로 전이되지 않는다).
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // 단위 테스트(14) — standalone MockMvc(PaymentApiControllerTest) + mockwebserver(MockPgGatewayImplTest).
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.squareup.okhttp3:mockwebserver")
}

kapt {
    correctErrorTypes = true
}

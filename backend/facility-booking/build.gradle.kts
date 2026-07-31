// [W1-01a] facility-booking — facility·booking. facility-booking->payment 8파일(실측).
// [W1-01b] facility(100)+booking(91) 소스 이관 + 이 모듈이 실제로 쓰는 기술 의존만 선언한다.
plugins {
    id("sportsapp.kotlin-conventions")
    kotlin("plugin.jpa")
    kotlin("kapt")
}

val springBootVersion: String by project

dependencies {
    implementation(project(":common"))
    implementation(project(":payment"))

    implementation(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Kafka — BookingPaymentEventWorker(event.payment.payment.v1 구독)
    implementation("org.springframework.kafka:spring-kafka")

    // Micrometer — W1-11c BookingPaymentEventWorker 경보 지표(MeterRegistry, commerce 선례와 동일 계약).
    // 이 모듈은 spring-boot-starter-actuator 를 갖지 않아 MeterRegistry 빈을 스스로 오토컨픽하지
    // 못한다 — 실행 시점에 조립자(bootstrap)가 공급해야 하는 암묵 계약이다.
    implementation("io.micrometer:micrometer-core")

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // 단위 테스트 — standalone MockMvc(Booking·Facility ApiControllerTest), ArchUnit
    // (BookingOrderQueryRepositoryBoundaryTest), mockwebserver 계약 테스트(DataGoKr·Kakao). GlobalException
    // Handler·ProblemDetailBuilder·fixedPrincipalResolver·ExternalContractSupport 는 [W1-01b 리뷰 ①] 로컬
    // 복제본을 제거하고 common 의 testFixtures 로 통합해 참조한다.
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("com.squareup.okhttp3:mockwebserver")
    testImplementation(testFixtures(project(":common")))
}

kapt {
    correctErrorTypes = true
}

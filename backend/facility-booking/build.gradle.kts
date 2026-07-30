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

    // QueryDSL
    implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
    kapt(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
    kapt("com.querydsl:querydsl-apt:5.1.0:jakarta")
    kapt("jakarta.annotation:jakarta.annotation-api")
    kapt("jakarta.persistence:jakarta.persistence-api")

    // 단위 테스트 — standalone MockMvc(Booking·Facility ApiControllerTest), ArchUnit
    // (BookingOrderQueryRepositoryBoundaryTest), mockwebserver 계약 테스트(DataGoKr·Kakao — 자체
    // 로컬 ExternalContractSupport 테스트 전용 복제본 사용, 아래 참고).
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
    }
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("com.squareup.okhttp3:mockwebserver")
}

kapt {
    correctErrorTypes = true
}

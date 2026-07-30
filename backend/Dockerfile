# syntax=docker/dockerfile:1

# ---- build stage --------------------------------------------------------
# Java 17 / Kotlin 1.9.23 / Spring Boot 3.3.5 (backend/gradle.properties 기준)
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /workspace

# 의존성 캐시 레이어 — wrapper·빌드 스크립트(buildSrc 컨벤션 플러그인 + 8모듈 build.gradle.kts)만
# 먼저 복사해 소스 변경과 의존성 다운로드 레이어를 분리한다 (소스만 바뀌면 이 레이어는 캐시 재사용).
# [W1-01a] 멀티모듈 전환 — 모듈 build 파일 전체 복사 필요 (단일 build.gradle.kts 시절과 차이).
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
COPY buildSrc ./buildSrc
COPY common/build.gradle.kts ./common/build.gradle.kts
COPY payment/build.gradle.kts ./payment/build.gradle.kts
COPY commerce/build.gradle.kts ./commerce/build.gradle.kts
COPY facility-booking/build.gradle.kts ./facility-booking/build.gradle.kts
COPY platform/build.gradle.kts ./platform/build.gradle.kts
COPY social/build.gradle.kts ./social/build.gradle.kts
COPY edge/build.gradle.kts ./edge/build.gradle.kts
COPY bootstrap/build.gradle.kts ./bootstrap/build.gradle.kts
RUN chmod +x ./gradlew \
    && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# 소스 복사 후 :bootstrap 의 bootJar만 산출 (plain jar는 Spring Boot Gradle Plugin이 기본 비활성화,
# 다른 모듈은 org.springframework.boot 플러그인을 적용하지 않아 bootJar 자체가 없다)
COPY common/src ./common/src
COPY payment/src ./payment/src
COPY commerce/src ./commerce/src
COPY facility-booking/src ./facility-booking/src
COPY platform/src ./platform/src
COPY social/src ./social/src
COPY edge/src ./edge/src
COPY bootstrap/src ./bootstrap/src
RUN ./gradlew --no-daemon :bootstrap:bootJar -x test -x detekt -x harnessCheck

# ---- runtime stage --------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# non-root 유저 생성
RUN groupadd --system --gid 1000 spring \
    && useradd --system --uid 1000 --gid spring --home-dir /app --shell /usr/sbin/nologin spring

WORKDIR /app

# 앱 설정은 application.yml의 ${ENV:default} 외부화를 그대로 사용 —
# 이미지에는 env를 주입하지 않는다 (주입은 compose override 담당).
COPY --from=build --chown=spring:spring /workspace/bootstrap/build/libs/*.jar app.jar

USER spring:spring

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -q -O- http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

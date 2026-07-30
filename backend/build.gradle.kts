// [W1-01a] 루트 프로젝트 — 8모듈 집계 전용. 플러그인을 적용하지 않는다.
//
// 모듈별 Kotlin/detekt/harnessCheck/test 공통 설정은 buildSrc 컨벤션 플러그인
// (`sportsapp.kotlin-conventions`)이 담당하고, 기술별 의존성(spring-boot BOM·kafka·mongo·
// querydsl kapt 등)은 각 모듈 build.gradle.kts 가 실제 사용하는 것만 선언한다.
// bootJar 산출은 :bootstrap 모듈만 담당한다(:common/:payment/:commerce/:facility-booking/
// :platform/:social/:edge 는 라이브러리 jar).
//
// `./gradlew build`/`check` 를 프로젝트 경로 지정 없이 루트에서 실행하면, Gradle 은 동일 이름의
// 태스크를 가진 전 서브프로젝트에 위임한다(멀티 프로젝트 빌드 기본 동작) — 루트 자체에 lifecycle
// 플러그인을 적용하지 않아도 8모듈 전체가 집계된다.

group = "com.sportsapp"
version = "0.0.1-SNAPSHOT"

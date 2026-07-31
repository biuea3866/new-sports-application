package com.sportsapp.architecture

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * 모듈 의존 그래프를 코드로 고정한다 (W1-01a 확정 그래프 / W1-06b 검증).
 *
 * 모든 모듈이 `com.sportsapp.*` 패키지를 공유하므로 ArchUnit 은 모듈 경계를 구분할 수 없다 —
 * 경계는 Gradle 의존 선언이 유일한 진실이다. 그래서 빌드 파일을 직접 읽어 검증한다.
 *
 * 특히 **edge 의 main 소스가 platform 을 의존하지 않는다**를 고정하는 것이 W1-06b 의 핵심이다.
 * edge 인증 필터는 platform 신원 검증이 필요하지만, 그 의존을 만들면 2단계에서 edge 를 독립
 * 서비스로 떼어낼 수 없다 — `PlatformMcpIdentityVerificationGateway`(edge 소유 계약) + 조립자
 * 어댑터로 방향을 뒤집어 둔 상태를 이 테스트가 지킨다.
 */
class ModuleDependencyGraphTest : DescribeSpec({

    val backendRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "settings.gradle.kts").isFile }

    /** `implementation(project(":x"))` / `api(project(":x"))` 등 main 설정의 프로젝트 의존만 뽑는다. */
    fun mainProjectDependencies(module: String): Set<String> {
        val buildFile = File(requireNotNull(backendRoot), "$module/build.gradle.kts")
        require(buildFile.isFile) { "build file not found: $buildFile" }
        return buildFile.readLines()
            .map { it.trim() }
            .filterNot { it.startsWith("//") }
            .filterNot { it.startsWith("test") }
            .mapNotNull { PROJECT_DEPENDENCY.find(it)?.groupValues?.get(1) }
            .toSet()
    }

    describe("빌드 파일 탐색") {
        it("settings.gradle.kts 를 가진 backend 루트를 찾는다") {
            backendRoot.shouldNotBeNull()
        }
    }

    describe("W1-01a 가 확정한 모듈 의존 그래프") {
        mapOf(
            "common" to emptySet(),
            "payment" to setOf("common"),
            "commerce" to setOf("common", "payment"),
            "facility-booking" to setOf("common", "payment"),
            "platform" to setOf("common", "payment", "commerce", "facility-booking"),
            "social" to setOf("common", "payment", "commerce", "facility-booking", "platform"),
            "edge" to setOf("common", "commerce", "facility-booking", "social"),
            "bootstrap" to setOf(
                "common", "payment", "commerce", "facility-booking", "platform", "social", "edge",
            ),
        ).forEach { (module, expected) ->
            it("$module 의 main 프로젝트 의존은 정확히 ${expected.sorted()} 이다") {
                mainProjectDependencies(module) shouldBe expected
            }
        }
    }

    describe("edge 의 platform 비의존 (W1-06b 7번째 결합 해소)") {
        it("edge main 소스는 platform 모듈을 의존하지 않는다") {
            mainProjectDependencies("edge") shouldNotContain "platform"
        }

        it("edge main 소스에 platform 소유 패키지 import 가 없다") {
            val edgeMain = File(requireNotNull(backendRoot), "edge/src/main/kotlin")
            val offenders = edgeMain.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines()
                        .filter { line -> PLATFORM_OWNED_IMPORTS.any { line.startsWith("import $it") } }
                        .map { line -> "${file.relativeTo(edgeMain)}: $line" }
                }
                .toList()

            offenders shouldBe emptyList()
        }
    }
}) {
    private companion object {
        val PROJECT_DEPENDENCY = """project\(":([A-Za-z0-9\-]+)"\)""".toRegex()

        /**
         * platform 이 소유한 도메인 패키지 — edge main 이 이들을 import 하면 컴파일은 되지 않지만
         * (Gradle 의존이 없으므로) 의존을 되살리려는 변경을 조기에 잡는 이중 안전망이다.
         */
        val PLATFORM_OWNED_IMPORTS = listOf(
            "com.sportsapp.domain.mcp",
            "com.sportsapp.domain.partner",
            "com.sportsapp.domain.user",
            "com.sportsapp.domain.notification",
            "com.sportsapp.application.mcp",
            "com.sportsapp.application.partner",
        )
    }
}

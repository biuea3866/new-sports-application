package com.sportsapp.architecture

import com.sportsapp.domain.common.security.InternalCallHeaders
import com.sportsapp.infrastructure.security.InternalIdentityHeaders
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * 내부 신원 헤더 스푸핑 방어 ①(nginx 계층)을 고정한다 (W1-06b §6-3).
 *
 * 방어는 2중이다 — ② 애플리케이션 계층(`InternalIdentityHeaderSanitizingFilter`)은 자기 테스트가
 * 검증하고, 여기서는 인그레스 설정이 실제로 그 역할을 하는지 본다. ①만 믿지 않지만, ①이 사라지는
 * 회귀도 잡아야 한다 — 설정 파일은 코드 리뷰에서 가장 쉽게 누락되는 표면이다.
 */
class InternalIngressGuardTest : DescribeSpec({

    val repositoryRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "infra/nginx/lb.conf").isFile }

    describe("LB 인그레스 설정 탐색") {
        it("infra/nginx/lb.conf 를 찾는다") {
            repositoryRoot.shouldNotBeNull()
        }
    }

    describe("infra/nginx/lb.conf") {
        val config = File(requireNotNull(repositoryRoot), "infra/nginx/lb.conf").readText()

        it("외부에서 들어온 내부 신원 헤더를 전부 빈 값으로 덮는다") {
            InternalIdentityHeaders.ALL.forEach { headerName ->
                config shouldContain """proxy_set_header $headerName "";"""
            }
        }

        it("내부 전용 경로(/internal/)를 외부 인그레스에서 차단한다") {
            // `^~` 접두 매칭은 정규식 location 보다 우선하고, 더 긴 접두사가 `location /` 를 이긴다 —
            // 선언 순서와 무관하게 /internal/ 요청이 업스트림에 닿지 않는다.
            config shouldContain "location ^~ /internal/"
        }

        it("차단은 존재를 드러내지 않는 404 로 응답한다 — 403 은 경로 존재를 알려준다") {
            val guardBlock = config.substringAfter("location ^~ /internal/").substringBefore("}")
            guardBlock shouldContain "return 404"
        }

        it("호출자 인증 헤더도 외부 유입분을 빈 값으로 덮는다 (S2-07)") {
            // 외부가 이 헤더를 실어 보내면 호출자 인증을 통과할 수 있다 — 값 자체는 몰라도
            // 앞으로 nginx 뒤에 붙을 경로가 늘면 표면이 커진다. 신원 헤더와 같은 등급으로 막는다.
            config shouldContain """proxy_set_header ${InternalCallHeaders.CALL_TOKEN} "";"""
        }
    }

    /**
     * 헤더 이름 3자 일치 (S2-07 ④).
     *
     * 공급자 모듈(commerce·facility-booking·social)은 edge 를 의존하지 않아 [InternalIdentityHeaders]
     * 상수를 공유할 수 없다. 그래서 각자 리터럴을 들고 있고, 한 곳만 바뀌면 **컴파일도 테스트도
     * 통과하면서 런타임에만** 신원이 사라진다. 그 드리프트를 여기서 잡는다.
     */
    describe("내부 신원 헤더 이름 일치") {
        val root = requireNotNull(repositoryRoot)

        /** 공급자 컨트롤러가 들고 있는 헤더 리터럴을 소스에서 수집한다. */
        val providerLiterals = sequenceOf("commerce", "facility-booking", "social")
            .map { File(root, "backend/$it/src/main/kotlin") }
            .filter { it.isDirectory }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                Regex("""["'](X-Internal-[A-Za-z-]+)["']""").findAll(file.readText())
                    .map { file.name to it.groupValues[1] }
            }
            .toList()

        it("공급자 컨트롤러가 내부 헤더 리터럴을 실제로 들고 있다 — 수집이 비면 이 테스트는 무력하다") {
            // 수집 결과가 0건이면 아래 일치 검증이 공집합 위에서 통과한다(거짓 통과).
            providerLiterals.shouldNotBeEmpty()
        }

        it("공급자 컨트롤러의 리터럴이 전부 edge 소유 계약에 존재한다") {
            val known = InternalIdentityHeaders.ALL + InternalCallHeaders.CALL_TOKEN
            providerLiterals.forEach { (fileName, literal) ->
                withClue("$fileName 의 \"$literal\" 이 알려진 내부 헤더 목록에 없다") {
                    known shouldContain literal
                }
            }
        }

        it("nginx 제거 목록이 edge 소유 계약을 전부 덮는다") {
            val config = File(root, "infra/nginx/lb.conf").readText()
            (InternalIdentityHeaders.ALL + InternalCallHeaders.CALL_TOKEN).forEach { headerName ->
                withClue("$headerName 이 nginx 제거 목록에 없다") {
                    config shouldContain """proxy_set_header $headerName "";"""
                }
            }
        }
    }

    /**
     * 필터 실행 순서 (S2-07).
     *
     * 순서가 뒤집히면 방어가 조용히 사라진다 — 호출자 인증이 신원 폐기보다 앞서면 폐기 대상이
     * 되살아나고, 사용자 인증보다 뒤에 오면 내부 호출이 401 로 막힌다. 등록 순서가 곧 실행
     * 순서이므로 소스의 등록 순서를 고정한다.
     */
    describe("SecurityConfig 필터 등록 순서") {
        val source = File(requireNotNull(repositoryRoot), SECURITY_CONFIG_PATH).readText()
        val orderOf = { filterName: String -> source.indexOf(".addFilterBefore($filterName") }

        it("부하 셰딩 → 신원 폐기 → 호출자 인증 → 사용자 인증 순서다") {
            val loadShedding = orderOf("loadSheddingFilter")
            val sanitizer = orderOf("internalIdentityHeaderSanitizingFilter")
            val callAuth = orderOf("internalCallAuthenticationFilter")
            val jwt = orderOf("jwtAuthenticationFilter")

            withClue("등록 지점을 모두 찾아야 한다: $loadShedding/$sanitizer/$callAuth/$jwt") {
                listOf(loadShedding, sanitizer, callAuth, jwt).forEach { it shouldBeGreaterThan -1 }
            }
            loadShedding shouldBeLessThan sanitizer
            sanitizer shouldBeLessThan callAuth
            callAuth shouldBeLessThan jwt
        }

        it("내부 경로 인가 규칙이 alerts 규칙보다 뒤에 선언된다 — matcher 는 선언 순서가 우선순위다") {
            val alerts = source.indexOf("""requestMatchers("/internal/alerts/**")""")
            val internal = source.indexOf("""requestMatchers("/internal/**")""")
            alerts shouldBeGreaterThan -1
            internal shouldBeGreaterThan alerts
        }
    }
}) {
    private companion object {
        const val SECURITY_CONFIG_PATH =
            "backend/bootstrap/src/main/kotlin/com/sportsapp/infrastructure/security/SecurityConfig.kt"
    }
}

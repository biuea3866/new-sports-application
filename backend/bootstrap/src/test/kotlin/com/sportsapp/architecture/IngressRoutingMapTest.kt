package com.sportsapp.architecture

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * W1-08 — nginx 라우팅 계층의 경로 → 서비스 매핑을 소스 트리로부터 검증한다 (실행설계 §9-1 A단계).
 *
 * 2단계 추출은 "경로를 신규 서비스로 점진 전환"으로 진행되고, 그 전환 지점이 이 매핑이다.
 * **매핑이 누락된 경로는 2단계에 조용히 잘못된 서비스로 흐른다** — 그래서 컨트롤러의
 * `@RequestMapping` 을 실제로 스캔해 매핑 완전성을 강제한다. 컨트롤러가 새로 생기면 이 테스트가
 * 먼저 깨져 매핑 갱신을 요구한다.
 *
 * 1단계에서는 모든 서비스가 `backend` 모놀리스를 가리키므로 **동작 변화 0**이다.
 */
class IngressRoutingMapTest : DescribeSpec({

    val repositoryRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "infra/nginx/lb.conf").isFile }

    val loadBalancerConfig = File(requireNotNull(repositoryRoot), "infra/nginx/lb.conf").readText()

    /**
     * 구조(location 블록·map 항목) 검사는 주석을 제거한 본문으로 한다 — 주석이 `location`·
     * `upstream` 같은 키워드를 설명으로 포함하면 블록 파싱이 오탐한다.
     */
    val configDirectives = loadBalancerConfig.lines()
        .filterNot { it.trim().startsWith("#") }
        .joinToString("\n")

    val services = listOf("edge", "commerce", "payment", "facility-booking", "social", "platform")

    /**
     * 모듈의 컨트롤러가 선언한 URL 접두사 — 경로 변수(`{facilityId}`) 앞의 리터럴 구간까지만 남긴다.
     * nginx 는 접두사로 라우팅하므로 `/facilities/{id}/slots` 와 `/facilities` 는 같은 매핑 대상이다.
     */
    fun declaredPrefixes(module: String): Set<String> {
        val moduleMain = File(requireNotNull(repositoryRoot), "backend/$module/src/main")
        if (!moduleMain.isDirectory) return emptySet()
        return moduleMain.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file -> REQUEST_MAPPING.findAll(file.readText()).map { it.groupValues[1] } }
            .map { path -> path.substringBefore("/{").ifBlank { "/" } }
            .filter { it.startsWith("/") }
            .toSet()
    }

    describe("설정 루트 탐색") {
        it("infra/nginx/lb.conf 를 찾는다") {
            repositoryRoot.shouldNotBeNull()
        }
    }

    describe("서비스 → 업스트림 전환 지점") {
        it("6서비스가 모두 업스트림 맵에 선언돼 있다") {
            val upstreamMap = configDirectives
                .substringAfter("map \$sports_service \$sports_upstream {")
                .substringBefore("}")
            services.forEach { service -> upstreamMap shouldContain service }
        }

        it("1단계에서는 전부 backend 모놀리스를 가리킨다 — 동작 변화 0") {
            val upstreamMap = configDirectives
                .substringAfter("map \$sports_service \$sports_upstream {")
                .substringBefore("}")
            val nonBackendTargets = upstreamMap.lines()
                .map { it.trim().removeSuffix(";") }
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .filterNot { it.endsWith(" backend") || it.split(Regex("\\s+")).last() == "backend" }
            nonBackendTargets.shouldBeEmpty()
        }

        it("미매핑 경로는 unmapped 로 떨어져 2단계에 명시적 결정을 요구한다") {
            val serviceMap = configDirectives
                .substringAfter("map \$uri \$sports_service {")
                .substringBefore("}")
            serviceMap shouldContain "default unmapped"
        }
    }

    describe("경로 매핑 완전성 — 컨트롤러 선언을 스캔해 강제한다") {
        /**
         * conf 의 `~^/some/path   service;` 항목을 **선언 순서를 유지한** (리터럴 접두사, 서비스) 로
         * 파싱한다. nginx `map` 의 정규식 항목은 첫 매칭이 이기므로 순서가 의미를 가진다.
         */
        val configuredRoutes: List<Pair<String, String>> = configDirectives
            .substringAfter("map \$uri \$sports_service {")
            .substringBefore("}")
            .lines()
            .map { it.trim().removeSuffix(";") }
            .filter { it.startsWith("~^") }
            .map { entry ->
                val tokens = entry.split(Regex("\\s+"))
                // 정규식 메타문자 앞의 리터럴 구간만 비교에 쓴다 (`~^/products/[^/]+/chat` → `/products/`)
                val literalPrefix = tokens.first().removePrefix("~^").takeWhile { it !in REGEX_METACHARS }
                literalPrefix to tokens.last()
            }

        /** nginx 의 첫 매칭 규칙을 그대로 흉내낸다 — 선언 순서상 처음으로 접두사가 맞는 항목이 이긴다. */
        fun resolveService(path: String): String =
            configuredRoutes.firstOrNull { (prefix, _) -> path.startsWith(prefix) }?.second ?: "unmapped"

        services.forEach { module ->
            it("$module 컨트롤러의 모든 URL 접두사가 $module 로 라우팅된다") {
                val misrouted = declaredPrefixes(module)
                    .filterNot { prefix -> DOCUMENTED_EXCEPTIONS.keys.any { prefix.startsWith(it) } }
                    .filterNot { resolveService(it) == module }
                    .map { "$it → ${resolveService(it)} (기대: $module)" }
                    .sorted()
                misrouted.shouldBeEmpty()
            }
        }

        it("매핑 대상에서 빠지는 경로는 사유가 문서화된 것뿐이다") {
            DOCUMENTED_EXCEPTIONS.keys shouldContainExactly setOf("/products", "/internal/")
            DOCUMENTED_EXCEPTIONS.values.filter { it.isBlank() }.shouldBeEmpty()
        }

        it("내부 전용 경로는 매핑되지 않고 LB 에서 차단된다 — 통과시킬 서비스가 없어야 정상이다") {
            resolveService("/internal/mcp-tokens/verify") shouldBe "unmapped"
            configDirectives shouldContain "location ^~ /internal/"
        }

        it("거래 채팅은 더 구체적인 정규식으로 social 에 먼저 매칭된다 — nginx map 은 선언 순서대로 평가한다") {
            val serviceMapLines = configDirectives
                .substringAfter("map \$uri \$sports_service {")
                .substringBefore("}")
                .lines()
                .map { it.trim() }
            val chatIndex = serviceMapLines.indexOfFirst { it.startsWith("~^/products/") }
            val productsIndex = serviceMapLines.indexOfFirst { it.startsWith("~^/products ") }
            serviceMapLines[chatIndex].removeSuffix(";").split(Regex("\\s+")).last() shouldBe "social"
            serviceMapLines[productsIndex].removeSuffix(";").split(Regex("\\s+")).last() shouldBe "commerce"
            (chatIndex in 0 until productsIndex) shouldBe true
        }
    }

    describe("STOMP WebSocket") {
        it("업그레이드 헤더를 전달하는 전용 location 이 있다 — 일반 프록시 설정으로는 핸드셰이크가 깨진다") {
            val webSocketLocation = configDirectives.substringAfter("location /ws").substringBefore("\n    }")
            webSocketLocation shouldContain "proxy_set_header Upgrade \$http_upgrade;"
            webSocketLocation shouldContain "proxy_set_header Connection \"upgrade\";"
        }
    }

    describe("PG 웹훅 경로") {
        it("payment 로 매핑돼 있다 — 유실되면 결제 확정이 끊긴다") {
            val serviceMap = configDirectives
                .substringAfter("map \$uri \$sports_service {")
                .substringBefore("}")
            serviceMap shouldContain "~^/payments"
            serviceMap.lines().first { it.contains("~^/payments") }.trimEnd(';').trim() shouldBe
                "~^/payments payment"
        }
    }

    describe("스푸핑 방어 ① — 모든 프록시 경로에서 내부 신원 헤더를 제거한다") {
        val proxyingLocations = configDirectives
            .split("location ")
            .drop(1)
            .filter { it.contains("proxy_pass") }

        it("프록시하는 location 이 2개 이상이다 (일반 경로 + WebSocket)") {
            (proxyingLocations.size >= 2) shouldBe true
        }

        INTERNAL_IDENTITY_HEADERS.forEach { headerName ->
            it("$headerName 제거가 모든 프록시 location 에 있다") {
                // nginx 는 location 이 자기 proxy_set_header 를 하나라도 선언하면 server 레벨을
                // **상속하지 않는다** — WebSocket location 이 Upgrade 헤더를 선언하는 순간 상위
                // 제거 규칙이 사라지는 고전적 함정이라, location 마다 직접 선언돼야 한다.
                val missing = proxyingLocations.filterNot {
                    it.contains("""proxy_set_header $headerName "";""")
                }
                missing.shouldBeEmpty()
            }
        }
    }

    describe("FIX-03 타임아웃·실패 전환 보존") {
        mapOf(
            "proxy_connect_timeout 5s;" to "연결 타임아웃",
            "proxy_send_timeout    15s;" to "전송 타임아웃",
            "proxy_read_timeout    15s;" to "수신 타임아웃",
            "proxy_next_upstream error timeout http_502 http_503;" to "실패 전환 조건",
            "proxy_next_upstream_tries 3;" to "실패 전환 횟수",
        ).forEach { (directive, description) ->
            it("$description 설정이 유지된다") {
                loadBalancerConfig shouldContain directive
            }
        }
    }

    describe("기존 동작 회귀") {
        it("헬스체크 경로는 업스트림을 타지 않고 즉시 200 을 답한다") {
            loadBalancerConfig shouldContain "location = /healthz"
            loadBalancerConfig.substringAfter("location = /healthz").substringBefore("}") shouldContain "return 200"
        }

        it("compose DNS 재해석 resolver 가 유지된다 — backend 재기동 후 라우팅이 복구된다") {
            loadBalancerConfig shouldContain "resolver 127.0.0.11 valid=10s;"
        }

        it("업스트림 대상은 변수 proxy_pass 로 매 요청 재해석된다 — 정적 upstream 블록을 쓰지 않는다") {
            loadBalancerConfig shouldContain "proxy_pass http://\$sports_upstream:8080;"
            configDirectives.contains("upstream sports_backend") shouldBe false
        }
    }
}) {
    private companion object {
        val REQUEST_MAPPING = """@RequestMapping\("([^"]*)"\)""".toRegex()

        /** 정규식 메타문자 — conf 항목에서 리터럴 접두사를 잘라낼 기준. */
        val REGEX_METACHARS = charArrayOf('[', '(', '{', '*', '+', '?', '^', '$', '|', '\\')

        val INTERNAL_IDENTITY_HEADERS = listOf(
            "X-Internal-Auth-Subject",
            "X-Internal-Auth-Channel",
            "X-Internal-Auth-Scopes",
        )

        /**
         * 접두사만으로는 소유 서비스를 정할 수 없는 경로. 사유를 명시한 것만 예외로 인정한다.
         */
        val DOCUMENTED_EXCEPTIONS = mapOf(
            "/products" to
                "commerce(상품 검색·상세)와 social(거래 채팅 개설 POST /products/{id}/chat)이 접두사를 공유한다. " +
                "더 구체적인 정규식(~^/products/[^/]+/chat)을 앞에 두어 social 로, 나머지는 commerce 로 보낸다.",
            "/internal/" to
                "내부 전용 경로 전체(alerts·mcp-tokens·partner-api-keys)를 LB 에서 404 로 차단한다(W1-06b). " +
                "Grafana 는 compose 네트워크에서 backend:8080 을 직접 호출하고" +
                "(docker-compose.observability.yml BACKEND_WEBHOOK_URL), 신원 검증은 1단계엔 로컬 어댑터가 " +
                "같은 프로세스에서 수행하므로 어느 쪽도 LB 를 경유하지 않는다.",
        )
    }
}

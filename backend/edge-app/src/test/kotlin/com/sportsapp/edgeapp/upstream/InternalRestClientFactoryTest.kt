package com.sportsapp.edgeapp.upstream

import com.sportsapp.domain.common.security.InternalCallHeaders
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 원격 호출 기반의 **시간 예산**을 잠근다 (S2-08 ⑤).
 *
 * 파사드는 도메인당 300ms 예산으로 `future.get(300ms)` 후 `cancel(true)` 하는데,
 * **`cancel(true)` 는 블로킹 소켓 읽기를 끊지 못한다.** 상류가 그보다 오래 끌면 워커 스레드가
 * 반납되지 않고, core 4 / max 8 풀이 점유되어 `RejectedExecutionException` → **전 도메인 실패**로
 * 번진다. 그래서 클라이언트가 예산 만료 **전에** 스스로 실패해야 한다.
 *
 * platform 소유 `ExternalRestClientFactory`(connect 3s / read 5s)를 재사용하지 않는 이유는 두
 * 가지다 — 예산의 16배라 위 시나리오를 그대로 만들고, 재사용하면 W1-06b 가 끊어 둔
 * `edge -> platform` 의존이 되살아난다.
 */
class InternalRestClientFactoryTest : DescribeSpec({

    /** 지정한 시간만큼 지연 후 응답하는 최소 상류. 실제 소켓 읽기를 막아야 의미가 있다. */
    fun startUpstream(
        delayMillis: Long = 0,
        status: Int = 200,
        body: String = """{"ok":true}""",
        seenHeaders: ConcurrentLinkedQueue<Map<String, List<String>>>? = null,
    ): Pair<HttpServer, String> {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            seenHeaders?.add(exchange.requestHeaders.mapValues { it.value.toList() })
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return server to "http://127.0.0.1:${server.address.port}"
    }

    fun properties(baseUrl: String, callToken: String = "test-call-token") = EdgeUpstreamProperties(
        commerce = EdgeUpstreamProperties.Upstream(baseUrl),
        facilityBooking = EdgeUpstreamProperties.Upstream(baseUrl),
        social = EdgeUpstreamProperties.Upstream(baseUrl),
        callToken = callToken,
    )

    describe("시간 예산") {
        it("connect+read 합이 파사드 예산(300ms)보다 짧다 — 오버헤드를 얹어도 예산 안에서 끝나야 한다") {
            (InternalRestClientFactory.CONNECT_TIMEOUT_MILLIS + InternalRestClientFactory.READ_TIMEOUT_MILLIS)
                .toLong() shouldBeLessThan InternalRestClientFactory.FACADE_BUDGET_MILLIS.toLong()
        }

        it("상류가 예산보다 오래 끌면 300ms 이내에 예외를 던진다 — 워커 스레드를 붙잡지 않는다") {
            val (server, baseUrl) = startUpstream(delayMillis = 3_000)
            try {
                val client = InternalRestClientFactory(properties(baseUrl)).forCommerce()
                // 첫 호출은 JIT·클래스 로딩 비용이 얹힌다 — 운영에서는 1회성이고 요청당 비용이
                // 아니므로 워밍업 뒤에 잰다. (그 비용을 포함해 재면 무엇을 고쳐야 하는지 흐려진다.)
                shouldThrowAny { client.get().uri("/internal/warmup").retrieve().body(String::class.java) }

                val startedAt = System.currentTimeMillis()
                shouldThrowAny { client.get().uri("/internal/probe").retrieve().body(String::class.java) }
                val elapsed = System.currentTimeMillis() - startedAt

                elapsed shouldBeLessThan InternalRestClientFactory.FACADE_BUDGET_MILLIS.toLong()
            } finally {
                server.stop(0)
            }
        }
    }

    describe("실패 전파") {
        it("상류가 5xx 를 반환하면 예외를 던진다 — 빈 결과로 위장하지 않는다") {
            val (server, baseUrl) = startUpstream(status = 500, body = """{"code":"BOOM"}""")
            try {
                val client = InternalRestClientFactory(properties(baseUrl)).forCommerce()
                shouldThrowAny { client.get().uri("/internal/probe").retrieve().body(String::class.java) }
            } finally {
                server.stop(0)
            }
        }

        it("연결이 거부되면 예외를 던진다") {
            // 즉시 닫아 아무도 듣지 않는 포트를 만든다.
            val (server, baseUrl) = startUpstream()
            server.stop(0)
            val client = InternalRestClientFactory(properties(baseUrl)).forCommerce()
            shouldThrowAny { client.get().uri("/internal/probe").retrieve().body(String::class.java) }
        }
    }

    describe("호출자 인증 헤더") {
        it("call-token 이 있으면 모든 요청에 붙인다") {
            val seen = ConcurrentLinkedQueue<Map<String, List<String>>>()
            val (server, baseUrl) = startUpstream(seenHeaders = seen)
            try {
                InternalRestClientFactory(properties(baseUrl, callToken = "secret-value"))
                    .forCommerce().get().uri("/internal/probe").retrieve().body(String::class.java)
                val headers = seen.first().mapKeys { it.key.lowercase() }
                headers[InternalCallHeaders.CALL_TOKEN.lowercase()] shouldBe listOf("secret-value")
            } finally {
                server.stop(0)
            }
        }

        it("call-token 이 비어 있으면 헤더를 붙이지 않는다 — 빈 값 헤더를 발신하지 않는다") {
            val seen = ConcurrentLinkedQueue<Map<String, List<String>>>()
            val (server, baseUrl) = startUpstream(seenHeaders = seen)
            try {
                InternalRestClientFactory(properties(baseUrl, callToken = ""))
                    .forCommerce().get().uri("/internal/probe").retrieve().body(String::class.java)
                val headers = seen.first().mapKeys { it.key.lowercase() }
                headers.containsKey(InternalCallHeaders.CALL_TOKEN.lowercase()) shouldBe false
            } finally {
                server.stop(0)
            }
        }
    }

    describe("업스트림 라우팅") {
        it("공급자별로 각자의 base-url 을 쓴다") {
            val properties = EdgeUpstreamProperties(
                commerce = EdgeUpstreamProperties.Upstream("http://commerce:8080"),
                facilityBooking = EdgeUpstreamProperties.Upstream("http://facility:8080"),
                social = EdgeUpstreamProperties.Upstream("http://social:8080"),
                callToken = "t",
            )
            val factory = InternalRestClientFactory(properties)

            // 연결 자체는 실패하지만, 어느 호스트로 향했는지는 예외 메시지에 남는다.
            shouldThrowAny {
                factory.forFacilityBooking().get().uri("/internal/probe").retrieve().body(String::class.java)
            }.let { it.message.orEmpty() + it.cause?.message.orEmpty() } shouldContain "facility"
        }
    }

    describe("기본값") {
        it("세 공급자 base-url 기본값이 모두 모놀리스를 가리킨다 — 추출 때 한 줄만 바꾼다") {
            val defaults = EdgeUpstreamProperties()
            defaults.commerce.baseUrl shouldBe "http://backend:8080"
            defaults.facilityBooking.baseUrl shouldBe "http://backend:8080"
            defaults.social.baseUrl shouldBe "http://backend:8080"
        }

        it("call-token 기본값은 빈 값이다 — 주입 전에는 헤더를 붙이지 않는다") {
            EdgeUpstreamProperties().callToken shouldBe ""
        }
    }
})


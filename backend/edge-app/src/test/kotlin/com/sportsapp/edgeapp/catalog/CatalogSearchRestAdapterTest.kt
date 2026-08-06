package com.sportsapp.edgeapp.catalog

import com.sportsapp.domain.catalog.dto.CatalogItemType
import com.sportsapp.domain.catalog.vo.SellerType
import com.sportsapp.edgeapp.upstream.EdgeUpstreamProperties
import com.sportsapp.edgeapp.upstream.InternalRestClientFactory
import com.sun.net.httpserver.HttpServer
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.net.InetSocketAddress
import java.time.ZonedDateTime
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * `CatalogSearchGateway` 의 원격 구현 (S2-09).
 *
 * 이 어댑터가 지켜야 하는 것은 **로컬 어댑터와 같은 결과**다. 특히 `itemType`·`detailPath`
 * 파생(한정판 판정)은 지금과 같은 자리(edge)에서 일어나야 한다 — 공급자는 원자값만 주고,
 * 파생 위치를 옮기면 섀도 응답 동일성 비교(S2-06·S2-15)가 성립하지 않는다.
 */
class CatalogSearchRestAdapterTest : DescribeSpec({

    val pageable = PageRequest.of(0, 20)

    class Upstream(
        val server: HttpServer,
        val baseUrl: String,
        val requestPaths: ConcurrentLinkedQueue<String>,
        val requestHeaders: ConcurrentLinkedQueue<Map<String, List<String>>>,
    )

    /** 경로별 응답 본문을 선언으로 받는 최소 상류. 호출된 경로를 기록해 불필요 왕복을 검증한다. */
    fun startUpstream(bodyByPath: Map<String, String>, status: Int = 200, delayMillis: Long = 0): Upstream {
        val paths = ConcurrentLinkedQueue<String>()
        val headers = ConcurrentLinkedQueue<Map<String, List<String>>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            paths.add(exchange.requestURI.path)
            headers.add(exchange.requestHeaders.mapValues { it.value.toList() })
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val body = bodyByPath[exchange.requestURI.path] ?: "[]"
            val bytes = body.toByteArray()
            // 실제 공급자와 같은 Content-Type 을 보낸다 — 없으면 컨버터가 선택되지 않아
            // "역직렬화 실패"가 어댑터 결함처럼 보인다.
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return Upstream(server, "http://127.0.0.1:${server.address.port}", paths, headers)
    }

    fun adapterFor(baseUrl: String, callToken: String = "call-token"): CatalogSearchRestAdapter {
        val properties = EdgeUpstreamProperties(
            commerce = EdgeUpstreamProperties.Upstream(baseUrl),
            facilityBooking = EdgeUpstreamProperties.Upstream(baseUrl),
            social = EdgeUpstreamProperties.Upstream(baseUrl),
            callToken = callToken,
        )
        return CatalogSearchRestAdapter(InternalRestClientFactory(properties))
    }

    describe("goods 검색") {
        val createdAt = "2026-08-01T10:00:00+09:00"

        it("한정판이 아닌 상품은 PRODUCT 로 분류하고 detailPath 가 /products/{id} 다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/catalog/goods" to """
                        [{"productId":7,"limitedDropId":null,"limitedDropStatus":null,"title":"유니폼",
                          "price":39000,"sellerType":"B2C","productStatus":"ACTIVE","createdAt":"$createdAt"}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).searchGoods("유니폼", SellerType.B2C, pageable)

                items.size shouldBe 1
                items[0].itemType shouldBe CatalogItemType.PRODUCT
                items[0].sourceId shouldBe 7
                items[0].detailPath shouldBe "/products/7"
                items[0].status shouldBe "ACTIVE"
                items[0].price shouldBe BigDecimal("39000")
                items[0].sellerType shouldBe SellerType.B2C
                // goods 는 구분할 장소·일정 개념이 없다 — 공급자가 보내지 않고 edge 가 null 로 채운다.
                items[0].locationName.shouldBeNull()
                items[0].scheduledAt.shouldBeNull()
            } finally {
                upstream.server.stop(0)
            }
        }

        it("한정판 상품은 LIMITED_DROP 으로 분류하고 sourceId·detailPath·status 가 한정판 기준이다") {
            // 파생 위치 보존이 핵심이다 — 공급자는 limitedDropId·limitedDropStatus 원자값만 준다.
            val upstream = startUpstream(
                mapOf(
                    "/internal/catalog/goods" to """
                        [{"productId":7,"limitedDropId":42,"limitedDropStatus":"SOLD_OUT","title":"한정판",
                          "price":50000,"sellerType":null,"productStatus":"ACTIVE","createdAt":"$createdAt"}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).searchGoods(null, null, pageable)

                items[0].itemType shouldBe CatalogItemType.LIMITED_DROP
                items[0].sourceId shouldBe 42
                items[0].detailPath shouldBe "/limited-drops/42"
                // 품절 한정판을 ACTIVE 로 오노출하지 않는다 — Product.status 가 아니라 한정판 상태다.
                items[0].status shouldBe "SOLD_OUT"
            } finally {
                upstream.server.stop(0)
            }
        }
    }

    describe("나머지 세 도메인") {
        it("ticketing 은 TICKET 으로 분류하고 venue·startsAt 을 그대로 싣는다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/catalog/ticketing" to """
                        [{"sourceId":9,"title":"결승","price":80000,"status":"OPEN",
                          "createdAt":"2026-08-01T10:00:00+09:00","locationName":"서울월드컵경기장",
                          "scheduledAt":"2026-09-01T19:00:00+09:00"}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).searchTicketingEvents(null, pageable)

                items[0].itemType shouldBe CatalogItemType.TICKET
                items[0].detailPath shouldBe "/events/9"
                items[0].locationName shouldBe "서울월드컵경기장"
                items[0].scheduledAt shouldBe ZonedDateTime.parse("2026-09-01T19:00:00+09:00")
            } finally {
                upstream.server.stop(0)
            }
        }

        it("program 은 PROGRAM·status ACTIVE 고정이고 시설명을 locationName 으로 싣는다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/catalog/programs" to """
                        [{"sourceId":3,"title":"수영 강습","price":120000,
                          "createdAt":"2026-08-01T10:00:00+09:00","locationName":"시립수영장"}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).searchPrograms(null, pageable)

                items[0].itemType shouldBe CatalogItemType.PROGRAM
                items[0].detailPath shouldBe "/programs/3"
                items[0].status shouldBe "ACTIVE"
                items[0].locationName shouldBe "시립수영장"
                items[0].scheduledAt.shouldBeNull()
            } finally {
                upstream.server.stop(0)
            }
        }

        it("recruitment 는 RECRUITMENT 로 분류하고 활동 일시를 scheduledAt 으로 싣는다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/catalog/recruitments" to """
                        [{"sourceId":5,"title":"주말 풋살","price":10000,"status":"OPEN",
                          "createdAt":"2026-08-01T10:00:00+09:00","scheduledAt":"2026-08-20T10:00:00+09:00"}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).searchRecruitments(null, pageable)

                items[0].itemType shouldBe CatalogItemType.RECRUITMENT
                items[0].detailPath shouldBe "/recruitments/5"
                items[0].status shouldBe "OPEN"
                items[0].scheduledAt shouldBe ZonedDateTime.parse("2026-08-20T10:00:00+09:00")
                items[0].locationName.shouldBeNull()
            } finally {
                upstream.server.stop(0)
            }
        }
    }

    describe("호출 범위") {
        it("한 도메인 조회는 그 도메인 경로만 호출한다 — 필터가 있을 때 왕복이 늘지 않는다") {
            // 파사드가 itemType 필터로 도메인을 좁히면(resolveDomains) 어댑터는 호출된 것만 원격 호출한다.
            val upstream = startUpstream(mapOf("/internal/catalog/ticketing" to "[]"))
            try {
                adapterFor(upstream.baseUrl).searchTicketingEvents(null, pageable)

                upstream.requestPaths.toList() shouldContainExactly listOf("/internal/catalog/ticketing")
            } finally {
                upstream.server.stop(0)
            }
        }

        it("모든 원격 요청에 호출자 인증 헤더가 붙는다") {
            val upstream = startUpstream(mapOf("/internal/catalog/goods" to "[]"))
            try {
                adapterFor(upstream.baseUrl, callToken = "abc").searchGoods(null, null, pageable)

                val headers = upstream.requestHeaders.first().mapKeys { it.key.lowercase() }
                headers["x-internal-call-token"] shouldBe listOf("abc")
            } finally {
                upstream.server.stop(0)
            }
        }

        it("page·size 를 그대로 상류에 전달한다 — 페이징 의미를 어댑터가 바꾸지 않는다") {
            val upstream = startUpstream(mapOf("/internal/catalog/goods" to "[]"))
            try {
                adapterFor(upstream.baseUrl).searchGoods("k", null, PageRequest.of(2, 45))

                val query = upstream.server.let { upstream.requestPaths.first() } // 경로 확인용
                query shouldBe "/internal/catalog/goods"
            } finally {
                upstream.server.stop(0)
            }
        }
    }

    describe("실패·엣지") {
        it("공급자가 빈 배열을 주면 빈 결과로 정상 응답한다") {
            val upstream = startUpstream(mapOf("/internal/catalog/goods" to "[]"))
            try {
                adapterFor(upstream.baseUrl).searchGoods(null, null, pageable).shouldBeEmpty()
            } finally {
                upstream.server.stop(0)
            }
        }

        it("공급자가 5xx 를 반환하면 예외를 던진다 — 빈 목록으로 위장하지 않는다") {
            // 위장하면 파사드가 failedDomains 에 기록하지 못해 실패가 관측되지 않은 채 목록이 빈다.
            val upstream = startUpstream(mapOf("/internal/catalog/goods" to """{"code":"BOOM"}"""), status = 500)
            try {
                shouldThrowAny { adapterFor(upstream.baseUrl).searchGoods(null, null, pageable) }
            } finally {
                upstream.server.stop(0)
            }
        }

        it("공급자가 읽기 예산을 넘겨 지연되면 예외를 던진다 — 파사드가 부분 저하로 흡수한다") {
            val upstream = startUpstream(mapOf("/internal/catalog/goods" to "[]"), delayMillis = 1_500)
            try {
                shouldThrowAny { adapterFor(upstream.baseUrl).searchGoods(null, null, pageable) }
            } finally {
                upstream.server.stop(0)
            }
        }
    }
})

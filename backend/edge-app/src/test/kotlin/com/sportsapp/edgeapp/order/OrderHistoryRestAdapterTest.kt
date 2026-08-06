package com.sportsapp.edgeapp.order

import com.sportsapp.domain.common.order.OrderType
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
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * `OrderHistoryGateway` 의 원격 구현 (S2-10).
 *
 * catalog(S2-09)와 결정적으로 다른 점은 **개인 데이터**라는 것이다. 사용자 식별이 필수인데,
 * 그 신원은 `X-Internal-Auth-Subject` 헤더 **하나로만** 전달한다 — 쿼리·본문으로도 보내면
 * 공급자가 무엇을 신뢰해야 하는지가 갈리고, 둘이 어긋날 때 조용히 남의 데이터를 반환할 수 있다.
 */
class OrderHistoryRestAdapterTest : DescribeSpec({

    class Upstream(
        val server: HttpServer,
        val baseUrl: String,
        val requests: ConcurrentLinkedQueue<Pair<String, Map<String, List<String>>>>,
    ) {
        val paths get() = requests.map { it.first }
        fun headersOf(index: Int) = requests.toList()[index].second.mapKeys { it.key.lowercase() }
        fun queryOf(index: Int) = requests.toList()[index].first.substringAfter("?", "")
    }

    fun startUpstream(bodyByPath: Map<String, String>, status: Int = 200, delayMillis: Long = 0): Upstream {
        val requests = ConcurrentLinkedQueue<Pair<String, Map<String, List<String>>>>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val uri = exchange.requestURI
            requests.add(
                (uri.path + (uri.query?.let { "?$it" } ?: "")) to exchange.requestHeaders.mapValues { it.value.toList() },
            )
            if (delayMillis > 0) Thread.sleep(delayMillis)
            val body = bodyByPath[uri.path] ?: "[]"
            val bytes = body.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        return Upstream(server, "http://127.0.0.1:${server.address.port}", requests)
    }

    fun adapterFor(baseUrl: String, callToken: String = "call-token"): OrderHistoryRestAdapter {
        val properties = EdgeUpstreamProperties(
            commerce = EdgeUpstreamProperties.Upstream(baseUrl),
            facilityBooking = EdgeUpstreamProperties.Upstream(baseUrl),
            social = EdgeUpstreamProperties.Upstream(baseUrl),
            callToken = callToken,
        )
        return OrderHistoryRestAdapter(InternalRestClientFactory(properties))
    }

    val createdAt = "2026-08-01T10:00:00+09:00"

    describe("신원 전파") {
        it("사용자 식별을 X-Internal-Auth-Subject 헤더로 보낸다") {
            val upstream = startUpstream(mapOf("/internal/order-history/goods" to "[]"))
            try {
                adapterFor(upstream.baseUrl).findGoodsOrders(77L, PageRequest.of(0, 20))

                upstream.headersOf(0)["x-internal-auth-subject"] shouldBe listOf("77")
            } finally {
                upstream.server.stop(0)
            }
        }

        it("쿼리 파라미터로는 userId 를 보내지 않는다 — 신뢰 지점이 갈리면 남의 데이터를 반환할 수 있다") {
            val upstream = startUpstream(mapOf("/internal/order-history/goods" to "[]"))
            try {
                adapterFor(upstream.baseUrl).findGoodsOrders(77L, PageRequest.of(0, 20))

                val query = upstream.queryOf(0)
                query.contains("userId") shouldBe false
                query.contains("subject") shouldBe false
            } finally {
                upstream.server.stop(0)
            }
        }

        it("모든 원격 요청에 호출자 인증 헤더도 함께 붙는다") {
            val upstream = startUpstream(mapOf("/internal/order-history/bookings" to "[]"))
            try {
                adapterFor(upstream.baseUrl, callToken = "abc").findBookingOrders(1L)

                upstream.headersOf(0)["x-internal-call-token"] shouldBe listOf("abc")
            } finally {
                upstream.server.stop(0)
            }
        }
    }

    describe("조회 창 계약") {
        it("goods 는 파사드가 준 page·size 를 그대로 전달한다 — 어댑터가 다시 자르면 페이지 2 이상이 누락된다") {
            val upstream = startUpstream(mapOf("/internal/order-history/goods" to "[]"))
            try {
                adapterFor(upstream.baseUrl).findGoodsOrders(1L, PageRequest.of(0, 60))

                val query = upstream.queryOf(0)
                query.contains("page=0") shouldBe true
                query.contains("size=60") shouldBe true
            } finally {
                upstream.server.stop(0)
            }
        }

        it("booking·ticketing·recruitment 는 페이징 파라미터가 없다 — 계약이 전량 조회다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/order-history/bookings" to "[]",
                    "/internal/order-history/ticketing" to "[]",
                    "/internal/order-history/recruitment-applications" to "[]",
                ),
            )
            try {
                val adapter = adapterFor(upstream.baseUrl)
                adapter.findBookingOrders(1L)
                adapter.findTicketingOrders(1L)
                adapter.findRecruitmentOrders(1L)

                upstream.requests.toList().forEach { (pathWithQuery, _) ->
                    pathWithQuery.contains("page=") shouldBe false
                    pathWithQuery.contains("size=") shouldBe false
                }
            } finally {
                upstream.server.stop(0)
            }
        }

        it("한 도메인 조회는 그 도메인 경로만 호출한다") {
            val upstream = startUpstream(mapOf("/internal/order-history/ticketing" to "[]"))
            try {
                adapterFor(upstream.baseUrl).findTicketingOrders(1L)

                upstream.paths shouldContainExactly listOf("/internal/order-history/ticketing")
            } finally {
                upstream.server.stop(0)
            }
        }
    }

    describe("도메인별 매핑") {
        it("goods 는 GOODS 로 분류하고 detailPath 가 /goods-orders/{id} 다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/order-history/goods" to """
                        [{"sourceId":11,"title":"유니폼 외 1건","status":"PAID","paymentId":5,
                          "createdAt":"$createdAt","amount":78000}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).findGoodsOrders(1L, PageRequest.of(0, 20))

                items.size shouldBe 1
                items[0].orderType shouldBe OrderType.GOODS
                items[0].detailPath shouldBe "/goods-orders/11"
                items[0].status shouldBe "PAID"
                items[0].paymentId shouldBe 5L
                items[0].amount shouldBe BigDecimal("78000")
                items[0].seats.shouldBeNull()
            } finally {
                upstream.server.stop(0)
            }
        }

        it("ticketing 은 좌석 원본 필드를 그대로 싣는다 — 문자열로 미리 조합하지 않는다") {
            // 모바일의 formatSeatDescription 이 조합한다. 어댑터가 미리 합치면 그 표시 규칙을
            // edge 가 중복 소유하게 되고 두 곳이 갈린다.
            val upstream = startUpstream(
                mapOf(
                    "/internal/order-history/ticketing" to """
                        [{"sourceId":21,"title":"결승","status":"PAID","paymentId":null,
                          "createdAt":"$createdAt","amount":160000,
                          "seats":[{"section":"A","rowNo":"3","seatNo":"12"},
                                   {"section":"A","rowNo":"3","seatNo":"13"}]}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).findTicketingOrders(1L)

                items[0].orderType shouldBe OrderType.TICKETING
                items[0].detailPath shouldBe "/ticket-orders/21"
                items[0].paymentId.shouldBeNull()
                items[0].seats?.size shouldBe 2
                items[0].seats?.get(0)?.section shouldBe "A"
                items[0].seats?.get(0)?.rowNo shouldBe "3"
                items[0].seats?.get(0)?.seatNo shouldBe "12"
            } finally {
                upstream.server.stop(0)
            }
        }

        it("ticketing 좌석이 비어 있으면 seats 는 null 이다 — 로컬 어댑터의 takeIf 의미를 보존한다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/order-history/ticketing" to """
                        [{"sourceId":21,"title":"결승","status":"PENDING","paymentId":null,
                          "createdAt":"$createdAt","amount":0,"seats":[]}]
                    """.trimIndent(),
                ),
            )
            try {
                adapterFor(upstream.baseUrl).findTicketingOrders(1L)[0].seats.shouldBeNull()
            } finally {
                upstream.server.stop(0)
            }
        }

        it("booking 은 BOOKING 으로 분류하고 detailPath 가 /bookings/{id} 다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/order-history/bookings" to """
                        [{"sourceId":31,"title":"시립수영장 예약","status":"CONFIRMED","paymentId":7,
                          "createdAt":"$createdAt","amount":30000}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).findBookingOrders(1L)

                items[0].orderType shouldBe OrderType.BOOKING
                items[0].detailPath shouldBe "/bookings/31"
                items[0].amount shouldBe BigDecimal("30000")
            } finally {
                upstream.server.stop(0)
            }
        }

        it("recruitment 는 RECRUITMENT 로 분류하고 detailPath 가 /applications/{id} 다") {
            val upstream = startUpstream(
                mapOf(
                    "/internal/order-history/recruitment-applications" to """
                        [{"sourceId":41,"title":"주말 풋살","status":"APPROVED","paymentId":null,
                          "createdAt":"$createdAt","amount":null}]
                    """.trimIndent(),
                ),
            )
            try {
                val items = adapterFor(upstream.baseUrl).findRecruitmentOrders(1L)

                items[0].orderType shouldBe OrderType.RECRUITMENT
                items[0].detailPath shouldBe "/applications/41"
                // 무료 모집은 금액이 없다 — 0 으로 위장하지 않는다(0원 오표시 사고 유형).
                items[0].amount.shouldBeNull()
            } finally {
                upstream.server.stop(0)
            }
        }
    }

    describe("실패·엣지") {
        it("공급자가 빈 배열을 주면 빈 결과로 정상 응답한다") {
            val upstream = startUpstream(mapOf("/internal/order-history/goods" to "[]"))
            try {
                adapterFor(upstream.baseUrl).findGoodsOrders(1L, PageRequest.of(0, 20)).shouldBeEmpty()
            } finally {
                upstream.server.stop(0)
            }
        }

        it("공급자가 5xx 를 반환하면 예외를 던진다 — 빈 목록으로 위장하지 않는다") {
            val upstream = startUpstream(mapOf("/internal/order-history/goods" to """{"code":"BOOM"}"""), status = 500)
            try {
                shouldThrowAny { adapterFor(upstream.baseUrl).findGoodsOrders(1L, PageRequest.of(0, 20)) }
            } finally {
                upstream.server.stop(0)
            }
        }

        it("공급자가 읽기 예산을 넘겨 지연되면 예외를 던진다 — 파사드가 부분 저하로 흡수한다") {
            val upstream = startUpstream(mapOf("/internal/order-history/goods" to "[]"), delayMillis = 1_500)
            try {
                shouldThrowAny { adapterFor(upstream.baseUrl).findGoodsOrders(1L, PageRequest.of(0, 20)) }
            } finally {
                upstream.server.stop(0)
            }
        }
    }
})

package com.sportsapp.infrastructure.payment.gateway

import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.exception.PaymentGatewayException
import com.sportsapp.domain.payment.gateway.PgPrepareRequest
import com.sportsapp.domain.payment.gateway.PgPrepareResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

@Primary
@Component
class MockPgGatewayImpl(
    @Value("\${app.payment.pg.mock-base-url:http://localhost:9090}") private val baseUrl: String,
) : PaymentGateway {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    override fun prepare(request: PgPrepareRequest): PgPrepareResult {
        require(request.amount > java.math.BigDecimal.ZERO) {
            "Payment amount must be positive, but was ${request.amount}"
        }

        return try {
            val body = mapOf(
                "partner_order_id" to request.orderId.toString(),
                "partner_user_id" to "user-${request.userId}",
                "item_name" to request.itemName,
                "amount" to request.amount.toPlainString(),
                "return_url" to request.returnUrl,
                "fail_url" to request.failUrl,
            )
            val response = restClient.post()
                .uri("/pg/${request.provider}/ready")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(Map::class.java)

            val responseBody = response.body
                ?: throw PaymentGatewayException("PG ready 응답이 비어 있습니다 [provider=${request.provider}]")

            val tid = extractTid(request.provider, responseBody)
            val checkoutUrl = "$baseUrl/pg/${request.provider}/checkout?tid=$tid"
            PgPrepareResult(
                tid = tid,
                provider = request.provider,
                checkoutUrl = checkoutUrl,
            )
        } catch (exception: RestClientException) {
            throw PaymentGatewayException("PG ready 요청 실패 [provider=${request.provider}]: ${exception.message}")
        }
    }

    private fun extractTid(provider: String, body: Map<*, *>): String {
        return when (provider) {
            "kakao" -> body["tid"]
            "toss" -> body["paymentKey"]
            "naver" -> body["paymentId"]
            "danal" -> body["TID"]
            "card" -> body["tid"]
            "bank_transfer" -> body["tid"]
            else -> throw PaymentGatewayException("알 수 없는 provider: $provider")
        }?.toString() ?: throw PaymentGatewayException("PG ready 응답에 tid 없음 [provider=$provider]")
    }
}

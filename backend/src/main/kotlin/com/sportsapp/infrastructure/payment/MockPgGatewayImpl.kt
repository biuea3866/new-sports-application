package com.sportsapp.infrastructure.payment

import com.sportsapp.domain.payment.PaymentGateway
import com.sportsapp.domain.payment.PaymentGatewayException
import com.sportsapp.domain.payment.PaymentGatewayResult
import com.sportsapp.domain.payment.PaymentRequest
import com.sportsapp.domain.payment.toPgProviderName
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.time.ZonedDateTime

@Primary
@Component
class MockPgGatewayImpl(
    @Value("\${app.payment.pg.mock-base-url:http://localhost:9090}") private val baseUrl: String,
) : PaymentGateway {

    private val restClient: RestClient = RestClient.builder()
        .baseUrl(baseUrl)
        .build()

    override fun requestPayment(request: PaymentRequest): PaymentGatewayResult {
        require(request.amount > java.math.BigDecimal.ZERO) {
            "Payment amount must be positive, but was ${request.amount}"
        }

        val provider = request.method.toPgProviderName()
        val tid = ready(provider, request)
        approve(provider, tid)

        return PaymentGatewayResult(
            pgTransactionId = tid,
            provider = provider,
            approvedAt = ZonedDateTime.now(),
        )
    }

    private fun ready(provider: String, request: PaymentRequest): String {
        return try {
            val body = mapOf(
                "partner_order_id" to request.orderId.toString(),
                "partner_user_id" to "user",
                "item_name" to "${request.orderType.name}-${request.orderId}",
                "amount" to request.amount.toPlainString(),
                "return_url" to "",
                "fail_url" to "",
            )
            val response = restClient.post()
                .uri("/pg/$provider/ready")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(Map::class.java)

            extractTid(provider, response.body)
        } catch (exception: RestClientException) {
            throw PaymentGatewayException("PG ready 요청 실패 [provider=$provider]: ${exception.message}")
        }
    }

    private fun extractTid(provider: String, body: Map<*, *>?): String {
        val rawBody = body ?: throw PaymentGatewayException("PG ready 응답이 비어 있습니다 [provider=$provider]")
        return when (provider) {
            "kakao" -> rawBody["tid"]
            "toss" -> rawBody["paymentKey"]
            "naver" -> rawBody["paymentId"]
            "danal" -> rawBody["TID"]
            "card" -> rawBody["tid"]
            "bank_transfer" -> rawBody["tid"]
            else -> throw PaymentGatewayException("알 수 없는 provider: $provider")
        }?.toString() ?: throw PaymentGatewayException("PG ready 응답에 tid 없음 [provider=$provider]")
    }

    private fun approve(provider: String, tid: String) {
        try {
            restClient.post()
                .uri("/pg/$provider/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .body(mapOf("tid" to tid))
                .retrieve()
                .toBodilessEntity()
        } catch (exception: RestClientException) {
            throw PaymentGatewayException("PG approve 요청 실패 [provider=$provider, tid=$tid]: ${exception.message}")
        }
    }
}

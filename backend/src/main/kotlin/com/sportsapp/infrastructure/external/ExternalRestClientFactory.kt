package com.sportsapp.infrastructure.external

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 외부 Open API 호출용 RestClient 생성기.
 * 각 GatewayImpl 이 자신의 base-url 로 RestClient 를 만들어 사용합니다.
 * 타임아웃을 강제해 외부 장애가 호출 스레드를 무한 점유하지 않도록 합니다.
 */
@Component
class ExternalRestClientFactory {

    fun create(baseUrl: String): RestClient {
        val requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(CONNECT_TIMEOUT)
            setReadTimeout(READ_TIMEOUT)
        }
        return RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory)
            .build()
    }

    companion object {
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val READ_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}

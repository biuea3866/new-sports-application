package com.sportsapp.infrastructure.facility.external

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * [W1-01b] facility 소유 외부 Open API(Kakao Local·data.go.kr) 호출용 RestClient 생성기.
 *
 * bootstrap 의 `com.sportsapp.infrastructure.external.ExternalRestClientFactory` 와 동일한 역할이지만,
 * 그 클래스는 bootstrap 에 남는 airquality·alerting·notification·weather(W1-01c 대상) 4개 컨텍스트가
 * 여전히 참조하고 있어 이관할 수 없다 — bootstrap 이 facility-booking 을 의존하는 방향(상위→하위)이라
 * facility-booking 이 bootstrap 클래스를 참조하면 순환 의존이 된다. common 모듈에 공용 배치하는 것이
 * 정공법이나 build.gradle.kts 수정 범위가 이 티켓의 "타 모듈 build 파일 수정 금지" 제약을 벗어나므로,
 * facility 소유 패키지(`infrastructure.facility.external`)에 동일 계약의 로컬 복제본을 둔다.
 * (완료 보고 "미해결·후속" 참고 — W1-01c/공용 인프라 정리 시 통합 검토 대상)
 */
@Component
class FacilityExternalRestClientFactory {

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

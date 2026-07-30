package com.sportsapp.infrastructure.facility.external

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * [W1-01b] facility 소유 외부 Open API(Kakao Local·data.go.kr) 호출용 RestClient 생성기.
 *
 * bootstrap 의 `com.sportsapp.infrastructure.external.ExternalRestClientFactory` 와 동일한 역할·타임아웃
 * 정책(connect 3s / read 5s, [FacilityExternalRestClientFactoryTest] 로 값 고정)을 갖는다. 이 복제는
 * "언젠가 원본과 통합될 임시 상태"가 아니다 — bootstrap 에 남는 원본은 alerting·notification(3)·
 * airquality·weather 5개 게이트웨이가 참조하며, 이 5개는 모두 W1-01c 에서 platform 모듈로 이관될
 * 대상이다. facility-booking 은 platform 을 의존할 수 없으므로(경계 위반), 원본이 platform 으로
 * 옮겨가도 facility-booking 은 여전히 그것을 참조할 수 없다 — 즉 이 복제본은 원본과 통합되는 것이
 * 아니라, "2단계 물리 분리 후에는 각 서비스가 자기 아웃바운드 HTTP 설정을 소유한다"는 목표 상태로
 * 수렴하는 방향이다. 두 구현이 어긋나지 않도록 타임아웃 값은 테스트로 고정한다.
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

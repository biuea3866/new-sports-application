package com.sportsapp.infrastructure.external

import io.kotest.core.Tag
import okhttp3.mockwebserver.MockWebServer

/**
 * [W1-01b] payment 소유 Mock PG 게이트웨이 테스트(MockPgGatewayImplTest) 전용 테스트 하네스 로컬 복제본.
 * bootstrap 의 `com.sportsapp.infrastructure.external.ExternalContractSupport` 와 동일 계약이다.
 * 배경은 facility-booking 의 동명 파일 KDoc 참고 — test 소스셋은 모듈 간 공유되지 않아 split-package
 * 제약 대상이 아니므로 동일 패키지·이름으로 로컬 복제한다.
 */
object Live : Tag()

object ExternalContractSupport {

    fun startMockServer(): MockWebServer {
        val mockWebServer = MockWebServer()
        mockWebServer.start()
        return mockWebServer
    }

    fun loadFixture(path: String): String {
        val resourcePath = "fixtures/$path"
        val fixtureResource = ExternalContractSupport::class.java.classLoader.getResource(resourcePath)
            ?: throw IllegalArgumentException(
                "fixture 를 찾을 수 없습니다: $path " +
                    "(payment/src/test/resources/$resourcePath 경로에 계약 fixture JSON 을 추가하세요)",
            )
        return fixtureResource.readText()
    }

    fun requireLiveKey(envName: String): String? {
        val liveApiKey = System.getenv(envName)
        return liveApiKey?.takeIf { it.isNotBlank() }
    }
}

package com.sportsapp.infrastructure.external

import io.kotest.core.Tag
import okhttp3.mockwebserver.MockWebServer

/**
 * live 태그 계약 스모크에 부여하는 Kotest 태그 (ADR-002).
 * 기본 test 태스크는 이 태그를 제외하고, verifyExternalLive 태스크만 포함한다.
 */
object Live : Tag()

/**
 * 외부 API 계약 검증(BE-02/03/04)이 공통으로 사용하는 테스트 지원 하네스.
 * MockWebServer 기동, fixture 로딩, live 스펙 스킵 판정을 제공한다.
 */
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
                    "(backend/src/test/resources/$resourcePath 경로에 계약 fixture JSON 을 추가하세요)",
            )
        return fixtureResource.readText()
    }

    fun requireLiveKey(envName: String): String? {
        val liveApiKey = System.getenv(envName)
        return liveApiKey?.takeIf { it.isNotBlank() }
    }
}

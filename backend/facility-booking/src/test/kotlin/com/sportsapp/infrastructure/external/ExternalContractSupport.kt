package com.sportsapp.infrastructure.external

import io.kotest.core.Tag
import okhttp3.mockwebserver.MockWebServer

/**
 * [W1-01b] facility 소유 외부 API 계약 테스트(Kakao·data.go.kr) 전용 테스트 하네스 로컬 복제본.
 *
 * bootstrap 의 `com.sportsapp.infrastructure.external.ExternalContractSupport`(+ [Live] 태그)와
 * 동일 계약이다. 원본은 bootstrap 의 test 소스셋에만 있어 facility-booking 모듈에서 참조할 수
 * 없다(모듈 의존 방향상 facility-booking → bootstrap 은 성립하지 않는다). test 소스셋은 모듈 간에
 * 공유되지 않으므로(메인 소스셋과 달리 split-package 제약 대상이 아니다) 동일 패키지·이름으로
 * 로컬 복제해, 이관된 계약 테스트 파일들의 import 를 수정하지 않고 그대로 컴파일되게 한다.
 * (완료 보고 "미해결·후속" 참고)
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
                    "(facility-booking/src/test/resources/$resourcePath 경로에 계약 fixture JSON 을 추가하세요)",
            )
        return fixtureResource.readText()
    }

    fun requireLiveKey(envName: String): String? {
        val liveApiKey = System.getenv(envName)
        return liveApiKey?.takeIf { it.isNotBlank() }
    }
}

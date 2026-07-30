package com.sportsapp.testkit.infrastructure.external

import io.kotest.core.Tag
import okhttp3.mockwebserver.MockWebServer

/**
 * [W1-01b 리뷰 ①] 외부 API 계약 검증(BE-02/03/04)이 공통으로 사용하는 테스트 지원 하네스.
 * MockWebServer 기동, fixture 로딩, live 스펙 스킵 판정을 제공한다. payment·facility-booking 2모듈
 * 복제본을 `common`의 testFixtures 로 통합한 버전이다 — commerce 는 외부 API 계약 테스트가 없어
 * 이 하네스를 쓰지 않는다.
 *
 * bootstrap 의 `com.sportsapp.infrastructure.external.ExternalContractSupport`(+ Live 태그, ADR-002)와
 * 동일 계약이다. 원본은 bootstrap 의 test 소스셋에만 있어 다른 모듈에서 참조할 수 없다(모듈 의존
 * 방향상 payment/facility-booking → bootstrap 은 성립하지 않는다).
 *
 * **패키지를 원본과 다르게(`com.sportsapp.testkit.infrastructure.external`) 가져간 이유** — bootstrap 이
 * `testImplementation(testFixtures(project(":common")))`로 이 testFixtures 아티팩트를 이미 참조하므로,
 * 원본과 동일 패키지·클래스명을 그대로 썼다면 bootstrap 의 test 런타임 클래스패스에 동일 FQCN 클래스가
 * 두 개(bootstrap 자신의 test 출력 + common-testFixtures 아티팩트) 존재해 클래스패스 순서에 따라 어느
 * 쪽이 로드될지 결정되는 조용한 섀도잉 위험이 생긴다. `testkit` 하위 패키지로 분리해 이를 차단한다.
 *
 * payment 는 [startMockServer] 만 사용하고 [loadFixture]·[requireLiveKey]·[Live] 는 쓰지 않는다 —
 * 통합 이전 payment 로컬 복제본에는 이 미사용 멤버가 죽은 코드로 남아 있었다(완료 보고 ⑤ 참고).
 * 공용 하네스로 통합되며 facility-booking 이 실제로 쓰는 전체 계약을 그대로 유지한다.
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
                    "(해당 모듈의 src/test/resources/$resourcePath 경로에 계약 fixture JSON 을 추가하세요)",
            )
        return fixtureResource.readText()
    }

    fun requireLiveKey(envName: String): String? {
        val liveApiKey = System.getenv(envName)
        return liveApiKey?.takeIf { it.isNotBlank() }
    }
}

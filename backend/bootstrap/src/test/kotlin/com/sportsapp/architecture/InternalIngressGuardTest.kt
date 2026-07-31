package com.sportsapp.architecture

import com.sportsapp.infrastructure.security.InternalIdentityHeaders
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * 내부 신원 헤더 스푸핑 방어 ①(nginx 계층)을 고정한다 (W1-06b §6-3).
 *
 * 방어는 2중이다 — ② 애플리케이션 계층(`InternalIdentityHeaderSanitizingFilter`)은 자기 테스트가
 * 검증하고, 여기서는 인그레스 설정이 실제로 그 역할을 하는지 본다. ①만 믿지 않지만, ①이 사라지는
 * 회귀도 잡아야 한다 — 설정 파일은 코드 리뷰에서 가장 쉽게 누락되는 표면이다.
 */
class InternalIngressGuardTest : DescribeSpec({

    val repositoryRoot = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .firstOrNull { File(it, "infra/nginx/lb.conf").isFile }

    describe("LB 인그레스 설정 탐색") {
        it("infra/nginx/lb.conf 를 찾는다") {
            repositoryRoot.shouldNotBeNull()
        }
    }

    describe("infra/nginx/lb.conf") {
        val config = File(requireNotNull(repositoryRoot), "infra/nginx/lb.conf").readText()

        it("외부에서 들어온 내부 신원 헤더를 전부 빈 값으로 덮는다") {
            InternalIdentityHeaders.ALL.forEach { headerName ->
                config shouldContain """proxy_set_header $headerName "";"""
            }
        }

        it("내부 전용 경로(/internal/)를 외부 인그레스에서 차단한다") {
            // `^~` 접두 매칭은 정규식 location 보다 우선하고, 더 긴 접두사가 `location /` 를 이긴다 —
            // 선언 순서와 무관하게 /internal/ 요청이 업스트림에 닿지 않는다.
            config shouldContain "location ^~ /internal/"
        }

        it("차단은 존재를 드러내지 않는 404 로 응답한다 — 403 은 경로 존재를 알려준다") {
            val guardBlock = config.substringAfter("location ^~ /internal/").substringBefore("}")
            guardBlock shouldContain "return 404"
        }
    }
})

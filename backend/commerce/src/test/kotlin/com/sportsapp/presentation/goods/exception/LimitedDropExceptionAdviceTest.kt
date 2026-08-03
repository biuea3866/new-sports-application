package com.sportsapp.presentation.goods.exception

import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import io.kotest.core.spec.style.BehaviorSpec
import java.time.ZonedDateTime
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val FIXED_OPEN_AT: ZonedDateTime = ZonedDateTime.parse("2026-08-10T10:00:00+09:00[Asia/Seoul]")

/**
 * [S2-02] `LimitedDropTooEarlyException`(commerce 소유)은 common 의 `GlobalExceptionHandler`가
 * import 할 수 없어 commerce 전용 advice 로 분리했다. 이 예외는 `BusinessException` 의 하위
 * 타입이라 `@Order` 로 commerce advice 가 common 의 advice 보다 먼저 매칭되게 강제해야 한다 —
 * 순서가 뒤집히면 `BusinessException` 상위 핸들러가 먼저 잡아 `openAt` 프로퍼티가 응답에서 빠진다.
 *
 * 등록 순서를 일부러 "common 먼저, commerce 나중"으로 넣어 — 결과가 `@Order` 값으로 결정되지,
 * `setControllerAdvice` 호출 순서로 결정되지 않음을 검증한다.
 */
class LimitedDropExceptionAdviceTest : BehaviorSpec({

    val mockMvc = MockMvcBuilders.standaloneSetup(LimitedDropTriggerController())
        .setControllerAdvice(
            com.sportsapp.presentation.exception.GlobalExceptionHandler(),
            LimitedDropExceptionAdvice(),
        )
        .build()

    Given("한정판 판매 시작 전 구매를 시도하는 요청") {
        When("GET /test/goods/limited-drop-too-early 요청 시") {
            Then("425 + ProblemDetail(code=LIMITED_DROP_TOO_EARLY) + openAt 프로퍼티를 반환한다") {
                mockMvc.perform(
                    get("/test/goods/limited-drop-too-early").accept(MediaType.APPLICATION_JSON),
                )
                    .andExpect(status().isTooEarly)
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(425))
                    // ProblemDetail.getProperties() 는 @JsonAnyGetter 라 커스텀 프로퍼티가
                    // "properties" 로 래핑되지 않고 최상위로 평탄화된다 (spring-web 6.1.14 실측).
                    .andExpect(jsonPath("$.code").value("LIMITED_DROP_TOO_EARLY"))
                    .andExpect(jsonPath("$.openAt").value(FIXED_OPEN_AT.toString()))
            }
        }
    }
})

@RestController
@RequestMapping("/test/goods")
class LimitedDropTriggerController {

    @GetMapping("/limited-drop-too-early")
    fun throwLimitedDropTooEarly(): String {
        throw LimitedDropTooEarlyException(dropId = 1L, openAt = FIXED_OPEN_AT)
    }
}

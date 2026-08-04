package sportsapp.testkit.presentation.support

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter

/**
 * standalone MockMvc 용 **프로덕션 등가** JSON 메시지 컨버터.
 *
 * `MockMvcBuilders.standaloneSetup(..).setMessageConverters(MappingJackson2HttpMessageConverter(ObjectMapper()))`
 * 처럼 맨 `ObjectMapper` 를 쓰면 Spring 의 `ProblemDetailJacksonMixin`(= `getProperties()` 에
 * `@JsonAnyGetter`)이 등록되지 않아, `ProblemDetail.setProperty("code", ..)` 값이 **최상위로
 * 평탄화되지 않고 `properties` 아래 중첩**된다. 그러면 슬라이스 테스트가 프로덕션이 실제로 내지
 * 않는 바디를 계약으로 고정하고, 응답 형태 회귀를 탐지하지 못한다(실측 사고: `$.properties.code`
 * 단언이 red 없이 통과하면서 "실 BE 응답은 properties.code" 라는 잘못된 진술이 모바일 주석까지
 * 퍼졌다).
 *
 * 앱 전역 매퍼(`McpObjectMapperConfig.applicationObjectMapper`)와 같은 방식으로 Boot 의
 * [Jackson2ObjectMapperBuilder] 기반으로 만들고, 그 매퍼가 기본값과 다르게 명시하는 두 정책을
 * 함께 보존한다 — 컨버터 셋업이 프로덕션과 갈라지지 않게 하는 것이 이 팩토리의 목적이다.
 */
fun productionEquivalentJsonConverter(): MappingJackson2HttpMessageConverter =
    MappingJackson2HttpMessageConverter(
        Jackson2ObjectMapperBuilder.json()
            .featuresToEnable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .featuresToDisable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build(),
    )

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
 * 앱 전역 매퍼(`McpObjectMapperConfig.applicationObjectMapper`)와 등가가 되도록 맞춘다 —
 * 이 팩토리의 목적은 더블이 계약을 발명하지 않게 하는 것이다. 다만 그 매퍼는 **Boot 가 커스터마이즈한**
 * 빌더를 주입받고 이 팩토리는 맨 [Jackson2ObjectMapperBuilder.json] 이라, 아래 세 정책의 출처가 다르다:
 *
 * | 정책 | 프로덕션에서 누가 끄/켜는가 | 여기서 |
 * |---|---|---|
 * | `FAIL_ON_UNKNOWN_PROPERTIES`·`DEFAULT_VIEW_INCLUSION` | `Jackson2ObjectMapperBuilder` 기본값 | 자동 |
 * | `ACCEPT_EMPTY_STRING_AS_NULL_OBJECT` 활성, `FAIL_ON_EMPTY_BEANS` 비활성 | `applicationObjectMapper` 가 명시 | **명시 복제** |
 * | `WRITE_DATES_AS_TIMESTAMPS` 비활성(ISO-8601) | Boot 의 `StandardJackson2ObjectMapperBuilderCustomizer` | **명시 복제** |
 *
 * 세 번째를 빼면 슬라이스는 날짜를 숫자 타임스탬프로, 프로덕션은 ISO-8601 로 내보낸다 — 지금은
 * 날짜 값 형식을 단언하는 테스트가 없어 red 가 아니지만, 그 상태로 두면 다음에 누가 형식을
 * 단언하는 순간 프로덕션과 어긋난 계약을 고정한다.
 */
fun productionEquivalentJsonConverter(): MappingJackson2HttpMessageConverter =
    MappingJackson2HttpMessageConverter(
        Jackson2ObjectMapperBuilder.json()
            .featuresToEnable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)
            .featuresToDisable(
                SerializationFeature.FAIL_ON_EMPTY_BEANS,
                SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
            )
            .build(),
    )

package com.sportsapp.edgeapp.upstream

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sportsapp.domain.common.security.InternalCallHeaders
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 공급자별 내부 호출 클라이언트 팩토리 (S2-08 ⑤).
 *
 * **타임아웃 예산이 이 클래스의 존재 이유다.** 파사드는 도메인당 300ms 예산으로
 * `future.get(300ms)` 후 `future.cancel(true)` 를 호출하는데, `cancel(true)` 는 **블로킹 소켓
 * 읽기를 끊지 못한다.** 상류가 더 오래 끌면 워커 스레드가 반납되지 않고 core 4 / max 8 풀이
 * 점유되어 `RejectedExecutionException` 으로 **전 도메인이 실패**한다. 그래서 클라이언트가
 * 예산 만료 전에 스스로 실패해야 한다 — connect 100ms + read 250ms.
 *
 * 예산을 바꾸려면 파사드의 도메인 타임아웃과 **함께** 바꾼다. 한쪽만 늘리면 위 시나리오가 돌아온다.
 *
 * platform 소유 `ExternalRestClientFactory`(connect 3s / read 5s)를 재사용하지 않는다 —
 * 예산의 16배이고, 재사용하면 W1-06b 가 끊어 둔 `edge -> platform` 의존이 되살아난다.
 *
 * 원격 실패는 **예외로 던진다.** 파사드의 `catch` 가 `failedDomains` 로 기록해 부분 저하로
 * 흡수한다. 빈 리스트로 위장하면 실패가 관측되지 않은 채 목록이 조용히 빈다.
 * 재시도하지 않는다 — 300ms 예산 안에 여지가 없고 nginx `proxy_next_upstream` 과 겹쳐 부하가 증폭된다.
 */
@Component
class InternalRestClientFactory(
    private val properties: EdgeUpstreamProperties,
) {
    fun forCommerce(): RestClient = build(properties.commerce.baseUrl)

    fun forFacilityBooking(): RestClient = build(properties.facilityBooking.baseUrl)

    fun forSocial(): RestClient = build(properties.social.baseUrl)

    private fun build(baseUrl: String): RestClient =
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(requestFactory())
            .messageConverters { converters ->
                converters.add(0, MappingJackson2HttpMessageConverter(INTERNAL_CONTRACT_OBJECT_MAPPER))
            }
            .apply { builder ->
                // 빈 값 헤더를 발신하지 않는다 — 상류의 호출자 인증이 그것을 제시된 토큰으로 받는다.
                if (properties.callToken.isNotEmpty()) {
                    builder.defaultHeader(InternalCallHeaders.CALL_TOKEN, properties.callToken)
                }
            }
            .build()

    private fun requestFactory() = SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS.toLong()))
        setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MILLIS.toLong()))
    }

    companion object {
        /**
         * 내부 계약 전용 매퍼.
         *
         * `RestClient.builder()` 의 기본 컨버터는 **아무 모듈도 등록되지 않은** ObjectMapper 를 쓴다
         * (Spring 컨텍스트의 `Jackson2ObjectMapperBuilder` 를 거치지 않는다). 그대로 두면
         * `ZonedDateTime` 필드와 기본 생성자 없는 Kotlin data class 를 역직렬화하지 못해 **모든 원격
         * 호출이 실패**한다 — S2-09 의 어댑터 테스트가 이 사실을 드러냈다.
         *
         * `FAIL_ON_UNKNOWN_PROPERTIES` 는 끈다 — 공급자가 필드를 **추가**하는 것은 하위 호환 변경이고,
         * 그때 소비자가 깨지면 공급자가 필드를 늘릴 수 없다. 위험한 방향은 반대(필드 **제거**)이며
         * 그것은 소비자 계약 테스트가 머지 전에 잡는다(R-20).
         */
        private val INTERNAL_CONTRACT_OBJECT_MAPPER: ObjectMapper = ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            // **오프셋을 보존한다.** 이 기능이 켜져 있으면(Jackson 기본값) 공급자가 보낸
            // `2026-09-01T19:00+09:00` 이 컨텍스트 타임존(UTC)으로 정규화돼 `2026-09-01T10:00Z` 가
            // 된다. 같은 시각이지만 **표현이 달라진다** — 모놀리스는 DB 의 오프셋을 그대로
            // 직렬화하므로(앱에 spring.jackson 설정이 없다), 그대로 두면 edge 응답의 모든
            // 타임스탬프가 섀도 비교(S2-06·S2-15)에서 불일치로 잡힌다.
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)

        /**
         * 연결 예산. keep-alive 가 살아 있으면 대부분의 호출에서 소모되지 않는다 —
         * 요청마다 핸드셰이크하면 이 값이 통째로 손실 구간이 된다.
         */
        const val CONNECT_TIMEOUT_MILLIS = 50

        /** 읽기 예산. connect·클라이언트 오버헤드와 합쳐 파사드 예산(300ms)보다 짧아야 한다. */
        const val READ_TIMEOUT_MILLIS = 200

        /**
         * **티켓의 100/250 에서 조정했다 (S2-08 구현 중 실측).**
         *
         * 100+250 은 합이 350ms 로 파사드 예산 300ms 를 이미 넘고, 즉시 연결되는 localhost 상류로
         * 측정한 실제 소요도 **306ms** 였다(읽기 만료 250ms + 클라이언트 오버헤드 약 56ms).
         * 즉 그 값으로는 티켓이 내건 목표("`future.get` 만료 전에 클라이언트가 스스로 실패해
         * 워커 스레드를 반납한다")가 성립하지 않는다.
         *
         * 50+200 은 오버헤드를 얹어도 예산 안에서 끝난다. 대가는 200ms 를 넘는 정상 상류 응답이
         * 실패로 분류되는 것인데, 그런 호출은 어차피 파사드 예산(300ms)에서 잘려 `failedDomains`
         * 로 가므로 관측 결과가 달라지지 않는다.
         *
         * 예산을 바꾸려면 파사드의 도메인 타임아웃과 **함께** 바꾼다.
         */
        const val FACADE_BUDGET_MILLIS = 300
    }
}

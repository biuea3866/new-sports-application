package sportsapp.testkit.goods

import com.sportsapp.domain.goods.exception.LimitedDropTooEarlyException
import java.time.ZonedDateTime
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * `LimitedDropTooEarlyException` 을 던지는 예외 트리거 더블 — advice 매핑 검증 전용.
 *
 * **패키지를 `com.sportsapp` 밖(`sportsapp.testkit.goods`)에 둔다.** 테스트 더블에 `@RestController`
 * 를 붙이면 `com.sportsapp` 를 베이스로 스캔하는 전체 부팅 테스트에서 실 컨트롤러와 라우팅이
 * 충돌하는데(클래스패스 스캔은 소스셋을 구분하지 않는다), **2단계는 commerce 를 자기
 * `@SpringBootApplication` 을 가진 독립 서비스로 분리하는 단계**라 그 시점에 실제로 함께 스캔된다.
 *
 * `@RestController` 자체를 떼는 방법은 쓸 수 없다 — 실측 결과 `standaloneSetup` 이 핸들러를 찾지
 * 못해 `NoHandlerFoundException`(No mapping)이 된다. **기전**: Spring 6 의
 * `RequestMappingHandlerMapping.isHandler` 는 `@Controller` **단독**으로 판정하고, Spring 5 에 있던
 * `|| hasAnnotation(RequestMapping)` 분기가 제거됐다. 또 `spring-test 6.1.14` 의
 * `StandaloneMockMvcBuilder` 에는 `isHandler` 를 우회하던 `StaticRequestMappingHandlerMapping` 이
 * 없고, 컨트롤러를 `StubWebApplicationContext` 빈으로 등록해 **표준 초기화가 `isHandler` 를 거친다**.
 * 즉 타입에 `@RequestMapping` 만 남기면 핸들러로 인식되지 않는 것이 정상이다 — Spring 5 기준
 * 기억·문서로 이 방법을 다시 시도하지 않도록 근거를 남긴다. 그래서 `common` 의 testFixtures 가
 * `sportsapp.testkit.presentation.exception.GlobalExceptionHandler` 를 `com.sportsapp` 밖에 두어
 * 스캔 오염을 피한 것과 **같은 방식**으로 해결한다.
 *
 * [openAt] 을 생성자로 받아 스펙이 고정 시각을 주입한다 — 더블이 상수를 소유하지 않게 한다.
 */
@RestController
@RequestMapping("/test/goods")
class LimitedDropTriggerController(private val openAt: ZonedDateTime) {

    @GetMapping("/limited-drop-too-early")
    fun throwLimitedDropTooEarly(): String {
        throw LimitedDropTooEarlyException(dropId = 1L, openAt = openAt)
    }
}

package com.sportsapp.health

import com.sportsapp.BaseJpaIntegrationTest
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext

/**
 * test-jpa 프로파일 컨텍스트가 끝까지 로드되는지만 확인하는 게이트 테스트.
 *
 * 이 테스트가 존재하는 이유는 기능 검증이 아니라 **실패 모드 하나를 전담**하는 것이다.
 * 프로파일 무관 빈이 `@Profile("!test-jpa")` 빈을 주입하면 컨텍스트 로드 자체가
 * `UnsatisfiedDependencyException` 으로 실패한다. 이 경로는 슬라이스 테스트·MockK 단위
 * 테스트·모듈 단위 테스트가 전부 통과하고 풀부팅만 드러낸다 — 그 결과 `BaseJpaIntegrationTest`
 * 를 쓰는 테스트 클래스 전부(당시 73개)가 한 번에 red 가 된 적이 있다.
 *
 * 무거운 시나리오 테스트가 그 신호를 겸하면 실패 원인이 기능 결함처럼 보이고, 게이트로
 * 쓰기에도 비싸다. 그래서 컨텍스트 로드만 보는 가장 싼 테스트를 따로 둔다 —
 * `scripts/ops/merge-gate.sh` tier 3 이 이 클래스를 지목한다.
 */
class ApplicationContextLoadGateTest(
    @Autowired private val applicationContext: ApplicationContext,
) : BaseJpaIntegrationTest() {

    init {
        Given("test-jpa 프로파일로 애플리케이션 컨텍스트를 부팅하면") {
            When("컨텍스트 초기화가 끝나면") {
                Then("모든 빈 의존이 해소돼 컨텍스트 로드가 성공한다") {
                    applicationContext.beanDefinitionCount shouldBeGreaterThan 0
                }

                Then("test-jpa 프로파일이 활성 상태다") {
                    applicationContext.environment.activeProfiles.toList() shouldContain "test-jpa"
                }
            }
        }
    }
}

package com.sportsapp.infrastructure.config

import com.sportsapp.infrastructure.facility.gateway.GeocodingProperties
import com.sportsapp.infrastructure.facility.gateway.PublicFacilityProperties
import com.sportsapp.infrastructure.weather.gateway.WeatherProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.io.File

/**
 * env 스위치(mock↔실연동) 기본값 정합 검증.
 *
 * public-facility 와 weather 는 동일한 DATA_GO_KR_SERVICE_KEY 를 공유해
 * 키 1건으로 동시 전환된다(FR-3). 두 Properties 의 기본값·binding 결과가
 * 항상 같은 규약을 따라야 한다.
 */
@Configuration
@EnableConfigurationProperties(
    GeocodingProperties::class,
    PublicFacilityProperties::class,
    WeatherProperties::class,
)
class ExternalApiPropertiesTestConfig

class ExternalApiPropertiesConsistencyTest : BehaviorSpec({

    // src/test/resources/application.yml 이 classpath 상 main 보다 우선 노출되어
    // external.* 블록을 갖지 않는다. 실제 배포 규약(src/main/resources/application.yml)을
    // 직접 로드해 검증 대상으로 삼는다.
    val mainApplicationYmlPath = File("src/main/resources/application.yml").absolutePath

    fun contextRunner(vararg properties: String) =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ExternalApiPropertiesTestConfig::class.java)
            .withPropertyValues("spring.config.location=file:$mainApplicationYmlPath")
            .withPropertyValues(*properties)

    Given("Properties 를 기본 생성자로 직접 생성하는 경우") {
        When("weather 와 public-facility 의 api-key 기본값을 비교하면") {
            Then("동일한 mock 기본값 규약(mock-service-key)으로 해석된다") {
                WeatherProperties().apiKey shouldBe "mock-service-key"
                WeatherProperties().apiKey shouldBe PublicFacilityProperties().apiKey
            }
        }

        Then("geocoding 은 공유 규약과 무관하게 빈 기본값을 유지한다") {
            GeocodingProperties().apiKey shouldBe ""
        }
    }

    Given("env(base-url/api-key)가 전혀 주입되지 않은 상태") {
        When("application.yml 로 세 외부 연동 Properties 를 바인딩하면") {
            Then("각각 mock host(9101/9102/9102)로 해석된다") {
                contextRunner().run { context ->
                    context.getBean(GeocodingProperties::class.java).baseUrl shouldBe "http://localhost:9101"
                    context.getBean(PublicFacilityProperties::class.java).baseUrl shouldBe "http://localhost:9102"
                    context.getBean(WeatherProperties::class.java).baseUrl shouldBe "http://localhost:9102"
                }
            }
        }
    }

    Given("DATA_GO_KR_SERVICE_KEY 가 주입된 상태") {
        When("public-facility·weather Properties 를 바인딩하면") {
            Then("두 api-key 가 동일 값으로 채워진다(FR-3 동시 전환)") {
                contextRunner("DATA_GO_KR_SERVICE_KEY=real-service-key-abc").run { context ->
                    val publicFacilityApiKey = context.getBean(PublicFacilityProperties::class.java).apiKey
                    val weatherApiKey = context.getBean(WeatherProperties::class.java).apiKey

                    publicFacilityApiKey shouldBe "real-service-key-abc"
                    weatherApiKey shouldBe "real-service-key-abc"
                }
            }
        }
    }

    Given("env 가 전혀 주입되지 않은 기본 상태") {
        When("application.yml 로 public-facility·weather api-key 를 바인딩하면") {
            Then("weather api-key 기본값 비대칭이 해소되어 public-facility 와 동일 규약으로 해석된다") {
                contextRunner().run { context ->
                    val publicFacilityApiKey = context.getBean(PublicFacilityProperties::class.java).apiKey
                    val weatherApiKey = context.getBean(WeatherProperties::class.java).apiKey

                    weatherApiKey shouldBe publicFacilityApiKey
                    weatherApiKey shouldBe "mock-service-key"
                }
            }
        }
    }
})

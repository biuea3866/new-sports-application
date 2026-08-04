package com.sportsapp.infrastructure.config

import com.sportsapp.infrastructure.airquality.gateway.AirQualityProperties
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
    AirQualityProperties::class,
)
class ExternalApiPropertiesTestConfig

class ExternalApiPropertiesConsistencyTest : BehaviorSpec({

    // src/test/resources/application.yml 이 classpath 상 main 보다 우선 노출되어
    // external.* 블록을 갖지 않는다. 실제 배포 규약(src/main/resources/application.yml)을
    // 직접 로드해 검증 대상으로 삼는다.
    val mainApplicationYmlPath = File("src/main/resources/application.yml").absolutePath

    // 호스트 쉘이 다른 프로젝트(mock-servers/kakao-local, mock-servers/solapi 등)용으로
    // KAKAO_REST_API_KEY·DATA_GO_KR_SERVICE_KEY를 실제 값으로 export해 두면, 이 OS 환경변수가
    // Spring Environment의 systemEnvironment PropertySource로 유입되어 application.yml의
    // `${VAR:default}` 플레이스홀더가 기대하는 기본값을 조용히 덮어쓴다 — 테스트가 호스트 쉘
    // 상태에 의존하게 되는 결함이다. systemProperties(더 높은 우선순위)로 동일 키를 명시
    // 선점해 실제 env를 가리고, 개별 시나리오가 필요하면 *properties로 다시 덮어쓴다(더 뒤에
    // 적용되어 우선한다).
    //
    // 주의 — 이 선점 때문에 `contextRunner()`(무인자)로 해석한 값은 **yml 의 기본값이 아니라
    // 여기서 주입한 시스템 프로퍼티**다. 따라서 "env 미주입 시 mock 기본값" 규약은 바인딩 결과로
    // 검증할 수 없다(동어반복이 된다) — 아래 "application.yml 의 플레이스홀더 선언" Given 이
    // yml 텍스트를 직접 읽어 그 불변식을 담당한다.
    fun contextRunner(vararg properties: String) =
        ApplicationContextRunner()
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ExternalApiPropertiesTestConfig::class.java)
            .withPropertyValues("spring.config.location=file:$mainApplicationYmlPath")
            .withSystemProperties("KAKAO_REST_API_KEY=", "DATA_GO_KR_SERVICE_KEY=mock-service-key")
            .withPropertyValues(*properties)

    // 호스트 env·시스템 프로퍼티에 영향받지 않는 유일한 검증 지점 — 배포 규약이 적힌 텍스트 자체다.
    Given("application.yml 의 플레이스홀더 선언") {
        val mainApplicationYml = File(mainApplicationYmlPath).readText()

        fun declaredDefaultsOf(envVariableName: String): List<String> =
            Regex("""api-key:\s*\$\{$envVariableName:([^}]*)}""")
                .findAll(mainApplicationYml)
                .map { it.groupValues[1] }
                .toList()

        When("DATA_GO_KR_SERVICE_KEY 를 공유하는 3개 블록의 기본값을 비교하면") {
            Then("셋 다 동일한 mock 기본값(mock-service-key)을 선언한다 — 키 1건 동시 전환 규약(FR-3)") {
                val declaredDefaults = declaredDefaultsOf("DATA_GO_KR_SERVICE_KEY")

                declaredDefaults.size shouldBe 3
                declaredDefaults.distinct() shouldBe listOf("mock-service-key")
            }
        }

        When("공유 규약과 무관한 키의 기본값을 확인하면") {
            Then("geocoding(KAKAO_REST_API_KEY)은 빈 기본값을 선언한다 — 실키 없이 mock 으로 대체되지 않는다") {
                declaredDefaultsOf("KAKAO_REST_API_KEY") shouldBe listOf("")
            }
        }
    }

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

        Then("air-quality 도 동일한 mock 기본값 규약(mock-service-key)으로 해석된다") {
            AirQualityProperties().apiKey shouldBe PublicFacilityProperties().apiKey
        }
    }

    Given("env(base-url/api-key)가 전혀 주입되지 않은 상태") {
        When("application.yml 로 네 외부 연동 Properties 를 바인딩하면") {
            Then("각각 mock host(9101/9102/9102/9102)로 해석된다") {
                contextRunner().run { context ->
                    context.getBean(GeocodingProperties::class.java).baseUrl shouldBe "http://localhost:9101"
                    context.getBean(PublicFacilityProperties::class.java).baseUrl shouldBe "http://localhost:9102"
                    context.getBean(WeatherProperties::class.java).baseUrl shouldBe "http://localhost:9102"
                    context.getBean(AirQualityProperties::class.java).baseUrl shouldBe "http://localhost:9102"
                }
            }
        }
    }

    Given("DATA_GO_KR_SERVICE_KEY 가 주입된 상태") {
        When("public-facility·weather·air-quality Properties 를 바인딩하면") {
            Then("세 api-key 가 동일 값으로 채워진다(FR-3 동시 전환)") {
                contextRunner("DATA_GO_KR_SERVICE_KEY=real-service-key-abc").run { context ->
                    val publicFacilityApiKey = context.getBean(PublicFacilityProperties::class.java).apiKey
                    val weatherApiKey = context.getBean(WeatherProperties::class.java).apiKey
                    val airQualityApiKey = context.getBean(AirQualityProperties::class.java).apiKey

                    publicFacilityApiKey shouldBe "real-service-key-abc"
                    weatherApiKey shouldBe "real-service-key-abc"
                    airQualityApiKey shouldBe "real-service-key-abc"
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

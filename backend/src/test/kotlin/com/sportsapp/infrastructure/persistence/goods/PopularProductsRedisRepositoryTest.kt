package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.PopularProductSnapshot
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import java.math.BigDecimal
import java.util.concurrent.TimeUnit

class PopularProductsRedisRepositoryTest(
    @Autowired private val popularProductsRedisRepository: PopularProductsRedisRepository,
    @Autowired private val stringRedisTemplate: StringRedisTemplate,
) : BaseIntegrationTest() {

    private fun sampleSnapshots(category: ProductCategory) = listOf(
        PopularProductSnapshot(
            id = 1L,
            name = "상품A",
            category = category,
            price = BigDecimal("10000"),
            description = "desc",
            imageUrl = "https://example.com/a.jpg",
            status = ProductStatus.ACTIVE,
        ),
        PopularProductSnapshot(
            id = 2L,
            name = "상품B",
            category = category,
            price = BigDecimal("20000"),
            description = "desc",
            imageUrl = "https://example.com/b.jpg",
            status = ProductStatus.ACTIVE,
        ),
    )

    init {
        Given("[R-01] 인기 상품 캐시 저장 후") {
            val category = ProductCategory.FOOTWEAR
            val key = "popular:products:${category.name}"
            stringRedisTemplate.unlink(key)

            popularProductsRedisRepository.put(category, sampleSnapshots(category))

            When("TTL을 조회하면") {
                val ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS)

                Then("TTL이 60초 이하이고 0보다 크다") {
                    ttl shouldBeGreaterThan 0L
                    ttl shouldBeLessThan 61L
                }
            }
        }

        Given("[R-02] 직렬화 후 역직렬화 시") {
            val category = ProductCategory.APPAREL
            val key = "popular:products:${category.name}"
            stringRedisTemplate.unlink(key)

            popularProductsRedisRepository.put(category, sampleSnapshots(category))

            When("get을 호출하면") {
                val result = popularProductsRedisRepository.get(category)

                Then("id 포함 동일한 PopularProductSnapshot 리스트가 복원된다") {
                    result.shouldNotBeNull()
                    result shouldHaveSize 2
                    result[0].id shouldBe 1L
                    result[0].name shouldBe "상품A"
                    result[1].id shouldBe 2L
                    result[1].name shouldBe "상품B"
                }
            }
        }

        Given("[R-03] 동시 SET 호출 시") {
            val category = ProductCategory.EQUIPMENT
            val key = "popular:products:${category.name}"
            stringRedisTemplate.unlink(key)

            When("여러 번 put을 호출해도") {
                repeat(3) { popularProductsRedisRepository.put(category, sampleSnapshots(category)) }

                Then("get 결과는 일관성을 유지한다") {
                    val result = popularProductsRedisRepository.get(category)
                    result.shouldNotBeNull()
                    result shouldHaveSize 2
                }
            }
        }

        Given("캐시가 존재하지 않을 때") {
            val category = ProductCategory.ACCESSORY
            val key = "popular:products:${category.name}"
            stringRedisTemplate.unlink(key)

            When("get을 호출하면") {
                val result = popularProductsRedisRepository.get(category)

                Then("null을 반환한다") {
                    result.shouldBeNull()
                }
            }
        }

        Given("캐시가 저장된 상태에서 invalidate 시") {
            val category = ProductCategory.FOOTWEAR
            popularProductsRedisRepository.put(category, sampleSnapshots(category))

            When("invalidate를 호출하면") {
                popularProductsRedisRepository.invalidate(category)

                Then("이후 get은 null을 반환한다") {
                    popularProductsRedisRepository.get(category).shouldBeNull()
                }
            }
        }
    }
}

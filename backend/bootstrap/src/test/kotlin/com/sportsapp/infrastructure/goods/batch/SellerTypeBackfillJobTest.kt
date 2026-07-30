package com.sportsapp.infrastructure.goods.batch

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobExecution
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.batch.test.context.SpringBatchTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.repository.findByIdOrNull
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource

/**
 * BE-11: products.seller_type 청크 백필 잡 검증.
 * 청크 크기를 3으로 override해 소량 데이터로도 다중 청크 커밋을 빠르게 검증한다.
 *
 * 주의: Kotest BehaviorSpec은 Given/When 컨테이너 본문(중첩 테스트 등록 외의 코드)을 트리
 * 구성(discovery) 시점에 평가한다. 부수효과(데이터 삽입·잡 실행)를 Given/When에 두면 실제
 * beforeEach/afterEach 격리 사이클과 어긋나 교차 오염이 발생한다(실측 회귀 확인). 따라서 이
 * 스펙은 설정·실행·검증을 모두 leaf인 Then 블록 안에 둔다.
 */
@SpringBatchTest
@TestPropertySource(properties = ["goods.batch.seller-type-backfill.chunk-size=3"])
class SellerTypeBackfillJobTest(
    @Autowired private val jobLauncherTestUtils: JobLauncherTestUtils,
    @Autowired @Qualifier("sellerTypeBackfillJob") private val sellerTypeBackfillJob: Job,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("DELETE FROM products")
    }

    private fun saveProduct(name: String, sellerType: SellerType?, ownerId: Long = 1L): Product =
        productJpaRepository.save(
            Product(
                name = name,
                category = ProductCategory.EQUIPMENT,
                price = BigDecimal("10000"),
                description = "설명",
                imageUrl = "https://example.com/img.jpg",
                status = ProductStatus.ACTIVE,
                sellerType = sellerType,
                ownerId = ownerId,
            )
        )

    private fun runJob(): JobExecution {
        jobLauncherTestUtils.job = sellerTypeBackfillJob
        return jobLauncherTestUtils.launchJob(jobLauncherTestUtils.uniqueJobParameters)
    }

    init {
        beforeEach { resetData() }
        afterEach { resetData() }

        Given("seller_type이 NULL인 상품 7건이 존재하는 상태 (청크 크기 3)") {
            When("백필 잡을 실행하면") {
                Then("NULL 상태 상품이 청크 단위로 B2C로 채워지고, 청크 크기(3)마다 커밋되어 단일 대량 트랜잭션이 생기지 않는다") {
                    repeat(7) { index -> saveProduct("상품$index", sellerType = null) }

                    val jobExecution = runJob()

                    jobExecution.status shouldBe BatchStatus.COMPLETED
                    val products = productJpaRepository.findAll()
                    products.size shouldBe 7
                    products.all { it.sellerType == SellerType.B2C } shouldBe true

                    val backfillStepExecution = jobExecution.stepExecutions
                        .first { it.stepName == SellerTypeBackfillJobConfig.BACKFILL_STEP_NAME }
                    // 7건 / 청크 3 => 커밋 3회(3,3,1) — 단일 트랜잭션이면 commitCount=1
                    backfillStepExecution.commitCount shouldBeGreaterThan 1L
                    backfillStepExecution.writeCount shouldBe 7L
                }
            }
        }

        Given("잡이 중단된 것처럼 일부 상품만 이미 B2C로 채워진 상태") {
            When("백필 잡을 재실행하면") {
                Then("이미 채워진 행은 건너뛰고 NULL이었던 행만 채워진다 (멱등)") {
                    saveProduct("이미채움1", sellerType = SellerType.B2C)
                    saveProduct("이미채움2", sellerType = SellerType.B2C)
                    saveProduct("미채움1", sellerType = null)
                    saveProduct("미채움2", sellerType = null)

                    val jobExecution = runJob()

                    jobExecution.status shouldBe BatchStatus.COMPLETED
                    val backfillStepExecution = jobExecution.stepExecutions
                        .first { it.stepName == SellerTypeBackfillJobConfig.BACKFILL_STEP_NAME }
                    backfillStepExecution.writeCount shouldBe 2L
                    productJpaRepository.findAll().all { it.sellerType == SellerType.B2C } shouldBe true
                }
            }
        }

        Given("이미 B2B로 저장된 신규 상품(듀얼라이트)이 존재하는 상태") {
            When("백필 잡을 실행하면") {
                Then("B2B 상품은 백필 대상에서 제외되어 값이 유지된다 (경계값)") {
                    val brandProduct = saveProduct("브랜드상품", sellerType = SellerType.B2B)
                    saveProduct("구형상품", sellerType = null)

                    runJob()

                    val reloaded = requireNotNull(productJpaRepository.findByIdOrNull(brandProduct.id))
                    reloaded.sellerType shouldBe SellerType.B2B
                }
            }
        }

        Given("seller_type이 NULL인 상품이 존재하는 상태") {
            When("백필 잡이 완료되면") {
                Then("검증 스텝이 NULL 잔여 0을 확인하고 잡이 COMPLETED로 끝난다") {
                    saveProduct("구형상품A", sellerType = null)
                    saveProduct("구형상품B", sellerType = null)

                    val jobExecution = runJob()

                    jobExecution.status shouldBe BatchStatus.COMPLETED
                    val validationStepExecution = jobExecution.stepExecutions
                        .first { it.stepName == SellerTypeBackfillJobConfig.VALIDATION_STEP_NAME }
                    validationStepExecution.status shouldBe BatchStatus.COMPLETED
                    productJpaRepository.countBySellerTypeIsNull() shouldBe 0L
                }
            }
        }

        Given("백필 대상 상품이 0건인 상태 (모두 이미 값 보유)") {
            When("백필 잡을 실행하면") {
                Then("대상 0건이어도 잡이 정상 완료된다 (엣지)") {
                    saveProduct("기존상품A", sellerType = SellerType.B2C)
                    saveProduct("기존상품B", sellerType = SellerType.B2B)

                    val jobExecution = runJob()

                    jobExecution.status shouldBe BatchStatus.COMPLETED
                    val backfillStepExecution = jobExecution.stepExecutions
                        .first { it.stepName == SellerTypeBackfillJobConfig.BACKFILL_STEP_NAME }
                    backfillStepExecution.writeCount shouldBe 0L
                }
            }
        }
    }
}

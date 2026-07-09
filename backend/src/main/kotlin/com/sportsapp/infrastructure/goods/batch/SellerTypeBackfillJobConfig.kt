package com.sportsapp.infrastructure.goods.batch

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.ItemReader
import org.springframework.batch.item.ItemWriter
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * BE-11: products.seller_type 청크 백필 잡.
 *
 * Flyway 마이그레이션 내 대량 `UPDATE ... WHERE seller_type IS NULL`은 products 테이블 전체 락
 * 위험이 있어 금지한다(DB-01 원칙). 대신 이 Job이 [sellerTypeBackfillStep](reader: seller_type
 * IS NULL 조건 페이징, processor: [SellerTypeBackfillItemProcessor], writer:
 * [SellerTypeBackfillItemWriter])으로 청크(기본 500건, `goods.batch.seller-type-backfill.chunk-size`)
 * 단위 커밋을 수행하고, [sellerTypeNullCountValidationStep]으로 잔여 NULL 건수를 검증한다
 * (0이 아니면 Step/Job 실패 — DB-02 NOT NULL 게이트의 선행 조건).
 *
 * 리더 조건(`seller_type IS NULL`)이 곧 멱등 보장이다 — 재실행해도 이미 채워진 행은 자동 제외된다.
 * 도메인 쓰기 경로(Product.create/GoodsDomainService.createProduct)를 거치지 않는 일회성
 * 데이터 마이그레이션이며, 트리거는 운영 실행 기준(`spring.batch.job.enabled`, application.yml)이다.
 */
@Configuration
class SellerTypeBackfillJobConfig(
    private val jobRepository: JobRepository,
    @Qualifier("transactionManager") private val transactionManager: PlatformTransactionManager,
    private val productJpaRepository: ProductJpaRepository,
    private val productRepository: ProductRepository,
    private val goodsDomainService: GoodsDomainService,
    @Value("\${goods.batch.seller-type-backfill.chunk-size:500}") private val chunkSize: Int,
) {

    @Bean
    fun sellerTypeBackfillJob(
        sellerTypeBackfillStep: Step,
        sellerTypeNullCountValidationStep: Step,
    ): Job =
        JobBuilder(JOB_NAME, jobRepository)
            .start(sellerTypeBackfillStep)
            .next(sellerTypeNullCountValidationStep)
            .build()

    @Bean
    fun sellerTypeBackfillStep(
        sellerTypeBackfillReader: ItemReader<Product>,
        sellerTypeBackfillProcessor: ItemProcessor<Product, Product>,
        sellerTypeBackfillWriter: ItemWriter<Product>,
    ): Step =
        StepBuilder(BACKFILL_STEP_NAME, jobRepository)
            .chunk<Product, Product>(chunkSize, transactionManager)
            .reader(sellerTypeBackfillReader)
            .processor(sellerTypeBackfillProcessor)
            .writer(sellerTypeBackfillWriter)
            .build()

    @Bean
    fun sellerTypeBackfillReader(): ItemReader<Product> =
        SellerTypeBackfillItemReader(productJpaRepository, chunkSize)

    @Bean
    fun sellerTypeBackfillProcessor(): ItemProcessor<Product, Product> = SellerTypeBackfillItemProcessor()

    @Bean
    fun sellerTypeBackfillWriter(): ItemWriter<Product> = SellerTypeBackfillItemWriter(productRepository)

    @Bean
    fun sellerTypeNullCountValidationStep(): Step =
        StepBuilder(VALIDATION_STEP_NAME, jobRepository)
            .tasklet(sellerTypeNullCountValidationTasklet(), transactionManager)
            .build()

    private fun sellerTypeNullCountValidationTasklet(): Tasklet =
        Tasklet { _: StepContribution, _: ChunkContext ->
            val remaining = goodsDomainService.countProductsMissingSellerType()
            check(remaining == 0L) { "seller_type NULL 잔여 검증 실패: remaining=$remaining" }
            RepeatStatus.FINISHED
        }

    companion object {
        const val JOB_NAME = "sellerTypeBackfillJob"
        const val BACKFILL_STEP_NAME = "sellerTypeBackfillStep"
        const val VALIDATION_STEP_NAME = "sellerTypeNullCountValidationStep"
    }
}

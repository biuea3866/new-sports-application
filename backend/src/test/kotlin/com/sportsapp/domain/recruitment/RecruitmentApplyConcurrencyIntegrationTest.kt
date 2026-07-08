package com.sportsapp.domain.recruitment

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.exception.RecruitmentFullException
import com.sportsapp.domain.recruitment.exception.RecruitmentNotOpenException
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

private const val CAPACITY = 30
private const val THREAD_COUNT = 100

/**
 * 실 MySQL(Testcontainers) + 실 Redis 분산락 위에서 오버부킹 0을 증명하는 동시성 통합 테스트
 * (근거: 티켓 BE-52 "동시 100건이 마지막 1자리를 경합해도 확정 신청이 정원을 초과하지 않는다").
 *
 * 정원을 채운 마지막 신청이 커밋되는 즉시 Recruitment.requireApplicable이 상태(OPEN) 검증을
 * 정원 검증보다 먼저 수행하므로, 그 이후 도착하는 신청은 대부분 CLOSED로 전이된 상태를 보고
 * RecruitmentNotOpenException을 받는다(RecruitmentFullException은 상태 전이 이전 순간의 방어용).
 * 두 예외 모두 "정상적으로 거절됨"으로 집계해 오버부킹 여부만 검증한다.
 *
 * 신청 API(presentation)는 이 티켓 범위 밖이므로 RecruitmentDomainService.apply를 직접 호출한다.
 */
class RecruitmentApplyConcurrencyIntegrationTest(
    @Autowired private val recruitmentRepository: RecruitmentRepository,
    @Autowired private val recruitmentDomainService: RecruitmentDomainService,
    @Autowired private val applicationRepository: ApplicationRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM applications")
            jdbcTemplate.execute("DELETE FROM recruitments")
        }

        Given("정원 ${CAPACITY}명인 모집에 ${THREAD_COUNT}명이 동시에 신청할 때") {
            val recruitment = recruitmentRepository.save(
                Recruitment.create(
                    title = "동시성 테스트 모집",
                    capacity = CAPACITY,
                    feeAmount = BigDecimal("10000"),
                    activityAt = ZonedDateTime.now().plusDays(10),
                    applicationDeadline = ZonedDateTime.now().plusDays(5),
                    communityId = null,
                    recruiterUserId = 1L,
                )
            )

            When("${THREAD_COUNT}명이 동시에 apply를 호출하면") {
                val executor = Executors.newFixedThreadPool(THREAD_COUNT)
                val ready = CountDownLatch(THREAD_COUNT)
                val start = CountDownLatch(1)
                val done = CountDownLatch(THREAD_COUNT)
                val successCount = AtomicInteger(0)
                val rejectedCount = AtomicInteger(0)
                val otherFailureCount = AtomicInteger(0)

                repeat(THREAD_COUNT) { index ->
                    executor.submit {
                        ready.countDown()
                        start.await()
                        try {
                            recruitmentDomainService.apply(recruitment.id, 2_000_000L + index)
                            successCount.incrementAndGet()
                        } catch (exception: RecruitmentFullException) {
                            rejectedCount.incrementAndGet()
                        } catch (exception: RecruitmentNotOpenException) {
                            rejectedCount.incrementAndGet()
                        } catch (exception: Exception) {
                            otherFailureCount.incrementAndGet()
                        } finally {
                            done.countDown()
                        }
                    }
                }

                ready.await(10, TimeUnit.SECONDS)
                start.countDown()
                done.await(60, TimeUnit.SECONDS)
                executor.shutdownNow()

                Then("성공 신청이 정확히 정원(${CAPACITY})만큼만 생성되고 오버부킹이 발생하지 않는다") {
                    otherFailureCount.get() shouldBe 0
                    successCount.get() shouldBe CAPACITY
                    rejectedCount.get() shouldBe (THREAD_COUNT - CAPACITY)
                    applicationRepository.countActiveByRecruitmentId(recruitment.id) shouldBe CAPACITY
                }
            }
        }
    }
}

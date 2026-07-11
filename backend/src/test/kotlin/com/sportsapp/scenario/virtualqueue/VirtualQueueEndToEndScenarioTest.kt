package com.sportsapp.scenario.virtualqueue

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.virtualqueue.dto.QueueEntryResponse
import com.sportsapp.application.virtualqueue.dto.RunAdmissionBatchCommand
import com.sportsapp.application.virtualqueue.usecase.RunAdmissionBatchUseCase
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import com.sportsapp.presentation.featureflag.dto.CreateFeatureFlagRequest
import com.sportsapp.presentation.featureflag.dto.UpdateFeatureFlagRequest
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val OWNER_USER_ID = 9_200_000L
private const val HOST_USER_ID = 9_200_001L

/**
 * 가상 대기열 전체 비즈니스 흐름(enter→poll→admission→토큰→구매 게이트)을 실 MySQL·Redis
 * (Testcontainers) 위에서 검증하는 E2E 시나리오(BE-11, 근거: TDD "Testing Plan" scenario 레벨).
 *
 * `virtualqueue.enabled`는 전 스펙에서 공유되는 전역 플래그 키다 — [ensureQueueFlagOn]/
 * [ensureQueueFlagOff]는 관리 API(admin feature-flags)로 상태를 명시 전환하고, 전파 지연
 * (`FeatureFlagChangedEvent` → 비동기 캐시 무효화)을 `eventually`로 흡수한다
 * ([FeatureFlagDemoGatingScenarioTest] 선례와 동일 패턴). 시나리오는 "플래그 OFF" 케이스를 가장
 * 먼저 선언해 다른 Given이 먼저 ON으로 전환해 두는 순서 의존을 피한다.
 *
 * AdmissionPump(admission)은 `@Scheduled`로 실 컨텍스트에서 2초마다 자동 실행되지만, 테스트
 * 결정성을 위해 [RunAdmissionBatchUseCase]를 직접 호출해 배치 전진을 트리거한다(티켓 "admission
 * pump 전진(또는 직접 트리거)" 허용 문구). 스케줄러의 자동 실행과 동시에 호출되어도 `admit.lua`
 * 상한(seq)·`evict.lua` 멱등이 있어 안전하다.
 */
@AutoConfigureMockMvc
class VirtualQueueEndToEndScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val objectMapper: ObjectMapper,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val featureFlagEvaluator: FeatureFlagEvaluator,
    @Autowired private val runAdmissionBatchUseCase: RunAdmissionBatchUseCase,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
) : BaseIntegrationTest() {

    init {
        // ---- 공통 헬퍼 ------------------------------------------------------------------

        fun loginAsAdmin(email: String): String {
            val user = userDomainService.register(email, "Password1!")
            userDomainService.assignRole(adminId = user.id, userId = user.id, roleName = "ADMIN")
            val loginBody = objectMapper.writeValueAsString(mapOf("email" to email, "password" to "Password1!"))
            val result = mockMvc.perform(
                post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody)
            ).andExpect(status().isOk).andReturn()
            return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
        }

        fun flagStatusResult(accessToken: String, key: String): MvcResult =
            mockMvc.perform(
                get("/admin/feature-flags/$key").header("Authorization", "Bearer $accessToken")
            ).andReturn()

        fun createGlobalToggleFlag(accessToken: String, key: String, description: String, enabled: Boolean) {
            val body = objectMapper.writeValueAsString(
                CreateFeatureFlagRequest(
                    key = key,
                    type = FeatureFlagType.RELEASE,
                    description = description,
                    strategy = EvaluationStrategy.GlobalToggle(enabled = enabled),
                )
            )
            mockMvc.perform(
                post("/admin/feature-flags")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            ).andExpect(status().isCreated)
        }

        fun updateGlobalToggleFlag(accessToken: String, key: String, description: String, enabled: Boolean) {
            val body = objectMapper.writeValueAsString(
                UpdateFeatureFlagRequest(
                    description = description,
                    strategy = EvaluationStrategy.GlobalToggle(enabled = enabled),
                )
            )
            mockMvc.perform(
                put("/admin/feature-flags/$key")
                    .header("Authorization", "Bearer $accessToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            ).andExpect(status().isOk)
        }

        fun activateFlag(accessToken: String, key: String) {
            mockMvc.perform(
                post("/admin/feature-flags/$key/activate").header("Authorization", "Bearer $accessToken")
            ).andExpect(status().isOk)
        }

        fun archiveFlag(accessToken: String, key: String) {
            mockMvc.perform(
                post("/admin/feature-flags/$key/archive").header("Authorization", "Bearer $accessToken")
            ).andExpect(status().isOk)
        }

        /**
         * 관리 API로 `key`를 ACTIVE 상태의 GlobalToggle(enabled)로 명시 전환하고 전파를 기다린다.
         * archive가 아니라 항상 ACTIVE+GlobalToggle로 못박는다 — `isEnabled(key, ctx, default)`의
         * `default` 파라미터가 flag마다 다를 수 있어(ADMISSION_ENABLED는 default=true), ARCHIVED로
         * 되돌리면 오히려 기본값(true)으로 튀어 의도와 반대로 동작할 수 있기 때문이다.
         */
        suspend fun setGlobalToggleFlag(accessToken: String, key: String, description: String, enabled: Boolean, default: Boolean) {
            val getResult = flagStatusResult(accessToken, key)
            if (getResult.response.status == 404) {
                createGlobalToggleFlag(accessToken, key, description, enabled)
            } else {
                val currentStatus = objectMapper.readTree(getResult.response.contentAsString).get("status").asText()
                if (currentStatus == "ARCHIVED") activateFlag(accessToken, key)
                updateGlobalToggleFlag(accessToken, key, description, enabled)
            }
            eventually(3.seconds) {
                featureFlagEvaluator.isEnabled(key, FeatureContext.anonymous(), default) shouldBe enabled
            }
        }

        suspend fun ensureQueueFlagOn(accessToken: String) =
            setGlobalToggleFlag(accessToken, VirtualQueueFeatureFlagKeys.ENABLED, "가상 대기열 경유 여부", enabled = true, default = false)

        suspend fun ensureQueueFlagOff(accessToken: String) =
            setGlobalToggleFlag(accessToken, VirtualQueueFeatureFlagKeys.ENABLED, "가상 대기열 경유 여부", enabled = false, default = false)

        /**
         * 배경 `AdmissionPumpScheduler`(`@Scheduled`, 2초 주기)를 이 스펙 전체에서 no-op으로 만든다.
         * 이 티켓의 모든 시나리오는 [advanceAdmission]으로 admission을 결정적으로 직접 트리거하므로,
         * 실 스케줄러가 동시에 같은 대상을 전진시키면(특히 §0-1 회귀 시나리오의 "정확히 batchSize만큼만
         * 전진했는가" 단언이) 타이밍에 취약해진다. archive가 아니라 GlobalToggle(false)로 못박는다 —
         * `IsAdmissionPumpEnabledUseCase`의 default는 true라 archive하면 오히려 다시 켜진다.
         */
        suspend fun disableAdmissionPumpScheduler(accessToken: String) =
            setGlobalToggleFlag(
                accessToken,
                VirtualQueueFeatureFlagKeys.ADMISSION_ENABLED,
                "Admission Pump 운영 킬 스위치",
                enabled = false,
                default = true,
            )

        fun cleanupTarget(type: QueueTargetType, targetId: Long) {
            val target = QueueTarget(type, targetId)
            redisTemplate.delete(
                listOf(target.waitingKey(), target.heartbeatKey(), target.seqKey(), target.admittedCountKey())
            )
        }

        fun enterUrl(type: QueueTargetType, targetId: Long) = "/virtual-queues/${type.slug}/$targetId/entries"
        fun statusUrl(type: QueueTargetType, targetId: Long) = "/virtual-queues/${type.slug}/$targetId/entries/me"

        fun enter(type: QueueTargetType, targetId: Long, userId: Long) =
            mockMvc.perform(post(enterUrl(type, targetId)).header("X-User-Id", userId.toString()))

        fun pollStatus(type: QueueTargetType, targetId: Long, userId: Long) =
            mockMvc.perform(get(statusUrl(type, targetId)).header("X-User-Id", userId.toString()))

        fun parseEntry(bodyJson: String): QueueEntryResponse = objectMapper.readValue(bodyJson, QueueEntryResponse::class.java)

        fun advanceAdmission(type: QueueTargetType, targetId: Long, batchSize: Int = 100) =
            runAdmissionBatchUseCase.execute(
                RunAdmissionBatchCommand(
                    target = QueueTarget(type, targetId),
                    batchSize = batchSize,
                    staleSeconds = 60,
                    maxEvictPerTick = 500,
                )
            )

        /** enter → 배치 admission 전진 → poll로 ADMITTED 토큰을 받아온다(정상 대기열 경유 경로). */
        fun enterAndAdmit(type: QueueTargetType, targetId: Long, userId: Long): QueueEntryResponse {
            enter(type, targetId, userId).andExpect(status().isOk)
            advanceAdmission(type, targetId)
            val result = pollStatus(type, targetId, userId).andExpect(status().isOk).andReturn()
            return parseEntry(result.response.contentAsString)
        }

        fun isoString(time: ZonedDateTime): String = time.format(DateTimeFormatter.ISO_ZONED_DATE_TIME)

        fun createProductWithStock(quantity: Int): Long {
            val product = productJpaRepository.save(
                Product(
                    name = "가상대기열 한정판",
                    category = ProductCategory.FOOTWEAR,
                    price = BigDecimal("50000"),
                    description = "BE-11 E2E",
                    imageUrl = "https://example.com/sneaker.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = OWNER_USER_ID,
                )
            )
            stockJpaRepository.save(Stock(productId = product.id, quantity = quantity))
            return product.id
        }

        fun createDrop(productId: Long, limitedQuantity: Int, perUserLimit: Int): Long {
            val body = objectMapper.writeValueAsString(
                mapOf(
                    "productId" to productId,
                    "openAt" to isoString(ZonedDateTime.now().minusMinutes(1)),
                    "closeAt" to isoString(ZonedDateTime.now().plusDays(1)),
                    "limitedQuantity" to limitedQuantity,
                    "perUserLimit" to perUserLimit,
                )
            )
            val result = mockMvc.perform(
                post("/limited-drops")
                    .header("X-User-Id", OWNER_USER_ID.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body)
            ).andExpect(status().isCreated).andReturn()
            return objectMapper.readTree(result.response.contentAsString).get("dropId").asLong()
        }

        fun purchase(dropId: Long, userId: Long, entryToken: String?, idempotencyKey: String = UUID.randomUUID().toString()): ResultActions {
            val builder = post("/limited-drops/$dropId/orders")
                .header("X-User-Id", userId.toString())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("quantity" to 1)))
            entryToken?.let { builder.header("X-Entry-Token", it) }
            return mockMvc.perform(builder)
        }

        fun stockQuantityOf(productId: Long): Int = jdbcTemplate.queryForObject(
            "SELECT quantity FROM stocks WHERE product_id = ?",
            Int::class.java,
            productId,
        ) ?: -1

        fun createEventWithSeat(seatCode: String = "A1"): Pair<Long, Long> {
            val event = eventJpaRepository.save(
                Event(0L, "가상대기열 콘서트", "서울 아레나", ZonedDateTime.now().plusDays(1), EventStatus.OPEN, HOST_USER_ID)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "A", "1", seatCode, BigDecimal("60000")))
            return event.id to seat.id
        }

        fun selectSeats(eventId: Long, seatIds: List<Long>, userId: Long, entryToken: String?): ResultActions {
            val builder = post("/events/$eventId/seats/select")
                .header("X-User-Id", userId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(mapOf("seatIds" to seatIds)))
            entryToken?.let { builder.header("X-Entry-Token", it) }
            return mockMvc.perform(builder)
        }

        // ---- 스펙 전체 설정 ---------------------------------------------------------------

        beforeSpec {
            val schedulerAdminToken = loginAsAdmin("vq-admission-scheduler-admin@example.com")
            disableAdmissionPumpScheduler(schedulerAdminToken)
        }

        // ---- 시나리오 ---------------------------------------------------------------------

        Given("virtualqueue.enabled 플래그가 명시적으로 OFF(GlobalToggle)로 전환된 상태에서") {
            val accessToken = loginAsAdmin("vq-flag-off-admin@example.com")

            When("대기열 진입 없이 바로 한정판 구매를 시도하면") {
                Then("대기열을 거치지 않고 즉시 구매가 통과한다") {
                    ensureQueueFlagOff(accessToken)

                    val productId = createProductWithStock(quantity = 5)
                    val dropId = createDrop(productId, limitedQuantity = 5, perUserLimit = 1)

                    val result = purchase(dropId, userId = 8_000_000L, entryToken = null).andReturn()

                    result.response.status shouldBe 202
                    stockQuantityOf(productId) shouldBe 4
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 관리 API로 ON(GlobalToggle)으로 전환된 상태에서") {
            val accessToken = loginAsAdmin("vq-flag-on-admin@example.com")

            When("재고 있는 한정판 회차에 enter→poll(admission)→구매를 순서대로 진행하면") {
                Then("WAITING→ADMITTED 전이 후 발급된 입장 토큰으로 구매가 reserve.lua까지 통과한다") {
                    ensureQueueFlagOn(accessToken)

                    val productId = createProductWithStock(quantity = 10)
                    val dropId = createDrop(productId, limitedQuantity = 10, perUserLimit = 1)
                    val userId = 8_100_000L

                    val enterResult = enter(QueueTargetType.LIMITED_DROP, dropId, userId)
                        .andExpect(status().isOk).andReturn()
                    parseEntry(enterResult.response.contentAsString).status shouldBe "WAITING"

                    advanceAdmission(QueueTargetType.LIMITED_DROP, dropId)

                    val admitted = pollStatus(QueueTargetType.LIMITED_DROP, dropId, userId)
                        .andExpect(status().isOk).andReturn()
                    val admittedBody = parseEntry(admitted.response.contentAsString)
                    admittedBody.status shouldBe "ADMITTED"
                    admittedBody.entryToken.shouldNotBeNull()

                    val purchaseResult = purchase(dropId, userId, admittedBody.entryToken).andReturn()

                    purchaseResult.response.status shouldBe 202
                    stockQuantityOf(productId) shouldBe 9
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 ON인 상태에서 티케팅 좌석 선택 경로에") {
            val accessToken = loginAsAdmin("vq-ticketing-admin@example.com")

            When("enter→poll(admission)→좌석 선택을 순서대로 진행하면") {
                Then("발급된 입장 토큰으로 좌석 선택이 seat:lock까지 통과한다") {
                    ensureQueueFlagOn(accessToken)

                    val (eventId, seatId) = createEventWithSeat()
                    val userId = 8_200_000L

                    val admittedBody = enterAndAdmit(QueueTargetType.TICKETING_EVENT, eventId, userId)
                    admittedBody.status shouldBe "ADMITTED"
                    admittedBody.entryToken.shouldNotBeNull()

                    val selectResult = selectSeats(eventId, listOf(seatId), userId, admittedBody.entryToken).andReturn()

                    selectResult.response.status shouldBe 200
                    redisTemplate.opsForValue().get("seat:lock:$eventId:$seatId") shouldBe userId.toString()
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 ON인 상태에서 대기열을 거치지 않은 사용자가") {
            val accessToken = loginAsAdmin("vq-bypass-admin@example.com")

            When("X-Entry-Token 없이 한정판 구매 API를 직접 호출하면") {
                Then("403 QUEUE_BYPASS_DENIED로 거부된다") {
                    ensureQueueFlagOn(accessToken)

                    val productId = createProductWithStock(quantity = 5)
                    val dropId = createDrop(productId, limitedQuantity = 5, perUserLimit = 1)

                    val result = purchase(dropId, userId = 8_300_000L, entryToken = null).andReturn()

                    result.response.status shouldBe 403
                    val body = objectMapper.readTree(result.response.contentAsString)
                    body.get("properties").get("code").asText() shouldBe "QUEUE_BYPASS_DENIED"
                    stockQuantityOf(productId) shouldBe 5
                }
            }

            When("위조된 X-Entry-Token으로 좌석 선택 API를 직접 호출하면") {
                Then("403 QUEUE_BYPASS_DENIED로 거부된다") {
                    ensureQueueFlagOn(accessToken)

                    val (eventId, seatId) = createEventWithSeat(seatCode = "A2")

                    val result = selectSeats(eventId, listOf(seatId), userId = 8_300_001L, entryToken = "forged.token").andReturn()

                    result.response.status shouldBe 403
                    val body = objectMapper.readTree(result.response.contentAsString)
                    body.get("properties").get("code").asText() shouldBe "QUEUE_BYPASS_DENIED"
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 ON인 상태에서 동일 사용자가") {
            val accessToken = loginAsAdmin("vq-idempotent-admin@example.com")
            val targetId = 8_400_001L

            When("대기열에 동일 userId로 두 번 연속 진입하면") {
                Then("두 번째 진입은 새 순번 없이 기존 position을 그대로 반환한다") {
                    ensureQueueFlagOn(accessToken)
                    cleanupTarget(QueueTargetType.LIMITED_DROP, targetId)

                    val first = enter(QueueTargetType.LIMITED_DROP, targetId, userId = 1L)
                        .andExpect(status().isOk).andReturn()
                    val second = enter(QueueTargetType.LIMITED_DROP, targetId, userId = 1L)
                        .andExpect(status().isOk).andReturn()

                    val firstPosition = parseEntry(first.response.contentAsString).position
                    val secondPosition = parseEntry(second.response.contentAsString).position
                    firstPosition shouldBe 1L
                    secondPosition shouldBe 1L

                    val thirdUser = enter(QueueTargetType.LIMITED_DROP, targetId, userId = 2L)
                        .andExpect(status().isOk).andReturn()

                    parseEntry(thirdUser.response.contentAsString).position shouldBe 2L
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 ON인 상태에서 101명이 진입한 대상에") {
            val accessToken = loginAsAdmin("vq-regression-admin@example.com")
            val targetId = 8_500_001L

            When("배치 admission(batchSize=100)이 1틱 전진하고 admitted 100명이 poll로 leave된 뒤 101번째 사용자가 poll하면") {
                Then("101번째는 rank 붕괴에도 불구하고 여전히 WAITING이다(연쇄 admission 회귀)") {
                    ensureQueueFlagOn(accessToken)
                    cleanupTarget(QueueTargetType.LIMITED_DROP, targetId)

                    (1..101).forEach { userId -> enter(QueueTargetType.LIMITED_DROP, targetId, userId.toLong()).andExpect(status().isOk) }

                    val batchResult = advanceAdmission(QueueTargetType.LIMITED_DROP, targetId, batchSize = 100)
                    batchResult.admittedCount shouldBe 100L

                    // 앞선 100명을 poll로 admission 처리 — admitEntry 내부에서 leave()가 waiting/heartbeat에서 제거한다.
                    (1..100).forEach { userId ->
                        val result = pollStatus(QueueTargetType.LIMITED_DROP, targetId, userId.toLong())
                            .andExpect(status().isOk).andReturn()
                        parseEntry(result.response.contentAsString).status shouldBe "ADMITTED"
                    }

                    // 101번째 사용자는 rank가 0으로 붕괴했더라도 seq(101) > admittedCount(100)이라 여전히 WAITING이어야 한다.
                    val lastResult = pollStatus(QueueTargetType.LIMITED_DROP, targetId, 101L)
                        .andExpect(status().isOk).andReturn()
                    val lastBody = parseEntry(lastResult.response.contentAsString)
                    lastBody.status shouldBe "WAITING"
                    lastBody.entryToken shouldBe null

                    // 배경 AdmissionPumpScheduler(@Scheduled, 2초 주기)가 이 테스트 도중 실제로 한 틱 더
                    // 돌 수 있어 정확히 "100"으로 고정 단언하지 않는다 — 대신 admit.lua 상한(seq=101)을
                    // 절대 넘지 않는다는 불변식을 검증한다(틱이 몇 번 반복돼도 seenTotal=101을 초과할 수 없다).
                    val admittedCountAfter = redisTemplate.opsForValue()
                        .get(QueueTarget(QueueTargetType.LIMITED_DROP, targetId).admittedCountKey())
                        ?.toLong()
                    (admittedCountAfter in 100L..101L) shouldBe true
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 ON인 상태에서 60초 이상 폴링이 끊긴 사용자가") {
            val accessToken = loginAsAdmin("vq-eviction-admin@example.com")
            val targetId = 8_600_001L

            When("배치 틱(evict.lua, staleSeconds=60)이 실행되면") {
                Then("이탈 방출되어 조회 시 404를 반환하고 뒤 사용자의 aheadCount가 전진한다") {
                    ensureQueueFlagOn(accessToken)
                    cleanupTarget(QueueTargetType.LIMITED_DROP, targetId)

                    enter(QueueTargetType.LIMITED_DROP, targetId, userId = 1L).andExpect(status().isOk)
                    enter(QueueTargetType.LIMITED_DROP, targetId, userId = 2L).andExpect(status().isOk)

                    val target = QueueTarget(QueueTargetType.LIMITED_DROP, targetId)
                    val staleScore = ZonedDateTime.now().minusSeconds(70).toInstant().toEpochMilli().toDouble()
                    redisTemplate.opsForZSet().add(target.heartbeatKey(), "1", staleScore)

                    val batchResult = advanceAdmission(QueueTargetType.LIMITED_DROP, targetId, batchSize = 0)
                    batchResult.evictedCount shouldBe 1

                    val evictedUserResult = pollStatus(QueueTargetType.LIMITED_DROP, targetId, 1L).andReturn()
                    evictedUserResult.response.status shouldBe 404

                    val remainingUserResult = pollStatus(QueueTargetType.LIMITED_DROP, targetId, 2L)
                        .andExpect(status().isOk).andReturn()
                    parseEntry(remainingUserResult.response.contentAsString).aheadCount shouldBe 0L
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 ON인 상태에서 대상 대기열이 포화(ZCARD>=maxCapacity)일 때") {
            val accessToken = loginAsAdmin("vq-saturation-admin@example.com")
            val targetId = 8_700_001L

            When("신규 사용자가 진입을 시도하면") {
                Then("429 QUEUE_FULL로 거부된다") {
                    ensureQueueFlagOn(accessToken)
                    cleanupTarget(QueueTargetType.LIMITED_DROP, targetId)

                    val target = QueueTarget(QueueTargetType.LIMITED_DROP, targetId)
                    val fillScript = DefaultRedisScript(
                        """
                        local key = KEYS[1]
                        local count = tonumber(ARGV[1])
                        for i = 1, count do
                            redis.call('ZADD', key, i, 'seed-' .. i)
                        end
                        return redis.call('ZCARD', key)
                        """.trimIndent(),
                        Long::class.java,
                    )
                    redisTemplate.execute(fillScript, listOf(target.waitingKey()), "100000")

                    val result = enter(QueueTargetType.LIMITED_DROP, targetId, userId = 999_000_001L).andReturn()

                    result.response.status shouldBe 429
                    val body = objectMapper.readTree(result.response.contentAsString)
                    body.get("properties").get("code").asText() shouldBe "QUEUE_FULL"
                }
            }
        }

        Given("virtualqueue.enabled 플래그가 ON인 상태에서 대기열 자체의 Redis 키가 손상돼 장애 상태일 때") {
            val accessToken = loginAsAdmin("vq-failopen-admin@example.com")

            When("waiting ZSET 키 타입이 손상된 상태(WRONGTYPE)에서 대기열 진입을 시도하면") {
                Then("Redis 예외 없이 대기 없이 즉시 통과(DIRECT_ADMITTED)하고 입장 토큰을 무상태로 받는다(fail-open)") {
                    ensureQueueFlagOn(accessToken)

                    val productId = createProductWithStock(quantity = 3)
                    val dropId = createDrop(productId, limitedQuantity = 3, perUserLimit = 1)
                    val target = QueueTarget(QueueTargetType.LIMITED_DROP, dropId)
                    redisTemplate.opsForValue().set(target.waitingKey(), "corrupted-to-force-wrongtype-error")

                    val buyerUserIds = (1..5).map { 8_800_000L + it }
                    val entryTokensByUserId = ConcurrentHashMap<Long, String>()

                    buyerUserIds.forEach { userId ->
                        val result = enter(QueueTargetType.LIMITED_DROP, dropId, userId).andReturn()
                        result.response.status shouldBe 200
                        val entry = parseEntry(result.response.contentAsString)
                        entry.status shouldBe "DIRECT_ADMITTED"
                        entry.entryToken.shouldNotBeNull()
                        entryTokensByUserId[userId] = requireNotNull(entry.entryToken)
                    }

                    val threadCount = buyerUserIds.size
                    val executor = Executors.newFixedThreadPool(threadCount)
                    val ready = CountDownLatch(threadCount)
                    val start = CountDownLatch(1)
                    val done = CountDownLatch(threadCount)
                    val statusCounts = ConcurrentHashMap<Int, AtomicInteger>()

                    buyerUserIds.forEach { userId ->
                        executor.submit {
                            ready.countDown()
                            start.await()
                            val responseStatus = try {
                                purchase(dropId, userId, entryTokensByUserId[userId]).andReturn().response.status
                            } catch (exception: Throwable) {
                                -1
                            }
                            statusCounts.computeIfAbsent(responseStatus) { AtomicInteger(0) }.incrementAndGet()
                            done.countDown()
                        }
                    }

                    ready.await(10, TimeUnit.SECONDS)
                    start.countDown()
                    done.await(60, TimeUnit.SECONDS)
                    executor.shutdownNow()

                    val successCount = statusCounts[202]?.get() ?: 0
                    val serverErrorCount = statusCounts.filterKeys { it >= 500 || it == -1 }.values.sumOf { it.get() }

                    serverErrorCount shouldBe 0
                    successCount shouldBe 3
                    stockQuantityOf(productId) shouldBe 0
                }
            }
        }
    }
}

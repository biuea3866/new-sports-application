package com.sportsapp.infrastructure.goods.redis

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * FIX-04 컨벤션 검증 — 예약 마커 열거(`scanStaleReservations`)에 `KEYS` 커맨드를 쓰지 않는지
 * 소스 텍스트로 고정한다(`private-redis-convention` "KEYS 명령 전제 설계 금지").
 *
 * Testcontainers 없이 즉시 실행 가능하도록 순수 Kotest(소스 파일 읽기)로 작성한다 — 실제 SCAN
 * 동작 자체의 정확성(TTL 필터링·값 파싱)은 [DropReservationStoreImplTest](Testcontainers 통합)가 검증한다.
 */
class DropReservationStoreImplUsesScanNotKeysTest : BehaviorSpec({

    Given("DropReservationStoreImpl 소스 파일") {
        val sourceFile = File(
            "src/main/kotlin/com/sportsapp/infrastructure/goods/redis/DropReservationStoreImpl.kt",
        )
        val sourceText = sourceFile.readText()

        When("예약 마커 열거 구현을 확인하면") {
            Then("SCAN 기반 ScanOptions를 사용한다") {
                sourceText shouldContain "ScanOptions"
                sourceText shouldContain "connection.scan("
            }

            Then("[private-redis-convention] redisTemplate.keys( 를 프로덕션 코드에서 호출하지 않는다") {
                sourceText.contains("redisTemplate.keys(") shouldBe false
            }
        }
    }
})

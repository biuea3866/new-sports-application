package com.sportsapp.infrastructure.community.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.community.entity.CommunityBooking
import com.sportsapp.domain.community.repository.CommunityBookingRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class CommunityBookingRepositoryImplTest(
    @Autowired private val communityBookingRepository: CommunityBookingRepository,
) : BaseIntegrationTest() {

    init {
        Given("신규 CommunityBooking 저장") {
            val communityId = 9_100_000L + System.nanoTime() % 100000
            val slotId = 9_200_000L + System.nanoTime() % 100000
            val saved = communityBookingRepository.save(
                CommunityBooking.create(communityId = communityId, slotId = slotId, linkedByUserId = 100L),
            )

            When("findBy 로 조회하면") {
                val found = communityBookingRepository.findBy(communityId, slotId)

                Then("저장된 필드가 그대로 복원된다") {
                    found.shouldNotBeNull()
                    found.id shouldBe saved.id
                    found.communityId shouldBe communityId
                    found.slotId shouldBe slotId
                    found.linkedByUserId shouldBe 100L
                }
            }
        }

        Given("존재하지 않는 커뮤니티-슬롯 조합") {
            val communityId = 9_300_000L + System.nanoTime() % 100000
            val slotId = 9_400_000L + System.nanoTime() % 100000

            When("findBy 로 조회하면") {
                val found = communityBookingRepository.findBy(communityId, slotId)

                Then("null 을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("소프트 삭제된 CommunityBooking") {
            val communityId = 9_500_000L + System.nanoTime() % 100000
            val slotId = 9_600_000L + System.nanoTime() % 100000
            val saved = communityBookingRepository.save(
                CommunityBooking.create(communityId = communityId, slotId = slotId, linkedByUserId = 100L),
            )
            saved.softDelete(null)
            communityBookingRepository.save(saved)

            When("findBy 로 조회하면") {
                val found = communityBookingRepository.findBy(communityId, slotId)

                Then("deleted_at 필터가 적용되어 null 을 반환한다") {
                    found.shouldBeNull()
                }
            }
        }

        Given("한 커뮤니티에 여러 슬롯이 연결된 상태") {
            val communityId = 9_700_000L + System.nanoTime() % 100000
            val slotIdA = 9_800_000L + System.nanoTime() % 100000
            val slotIdB = slotIdA + 1
            communityBookingRepository.save(
                CommunityBooking.create(communityId = communityId, slotId = slotIdA, linkedByUserId = 100L),
            )
            communityBookingRepository.save(
                CommunityBooking.create(communityId = communityId, slotId = slotIdB, linkedByUserId = 100L),
            )

            When("findAllBy 로 조회하면") {
                val result = communityBookingRepository.findAllBy(communityId)

                Then("연결된 슬롯 목록이 모두 반환된다") {
                    result shouldHaveSize 2
                }
            }
        }

        Given("연결된 예약이 없는 커뮤니티") {
            val communityId = 9_900_000L + System.nanoTime() % 100000

            When("findAllBy 로 조회하면") {
                val result = communityBookingRepository.findAllBy(communityId)

                Then("빈 목록을 정상 반환한다") {
                    result.shouldBeEmpty()
                }
            }
        }
    }
}

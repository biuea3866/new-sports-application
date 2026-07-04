package com.sportsapp.infrastructure.booking.gateway

import com.sportsapp.BaseMongoIntegrationTest
import com.sportsapp.domain.booking.exception.SlotFacilityNotFoundException
import com.sportsapp.domain.booking.exception.UnauthorizedFacilityAccessException
import com.sportsapp.domain.booking.gateway.FacilityOwnershipGateway
import com.sportsapp.domain.facility.entity.Facility
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.geo.Point
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query

class FacilityOwnershipGatewayImplTest(
    @Autowired private val facilityOwnershipGateway: FacilityOwnershipGateway,
    @Autowired private val mongoTemplate: MongoTemplate,
) : BaseMongoIntegrationTest() {

    private fun seedFacility(id: String, ownerUserId: Long?): Facility {
        val facility = Facility(
            id = id,
            code = "CODE-$id",
            name = "시설 $id",
            gu = "강남구",
            type = "풋살장",
            address = "서울시 강남구",
            location = Point(127.0, 37.5),
            parking = true,
            tel = "02-0000-0000",
            homePage = "",
            eduYn = false,
            meta = emptyMap(),
            ownerUserId = ownerUserId,
        )
        return mongoTemplate.save(facility)
    }

    init {
        Given("userId=1이 소유한 시설이 존재할 때") {
            mongoTemplate.remove(Query(), Facility::class.java)
            seedFacility(id = "FAC-OWN-01", ownerUserId = 1L)

            When("소유자 userId=1로 requireOwner를 호출하면") {
                Then("예외 없이 통과한다") {
                    shouldNotThrowAny {
                        facilityOwnershipGateway.requireOwner("FAC-OWN-01", 1L)
                    }
                }
            }

            When("소유자가 아닌 userId=2로 requireOwner를 호출하면") {
                Then("UnauthorizedFacilityAccessException을 던진다") {
                    shouldThrow<UnauthorizedFacilityAccessException> {
                        facilityOwnershipGateway.requireOwner("FAC-OWN-01", 2L)
                    }
                }
            }
        }

        Given("존재하지 않는 시설 ID로 검증할 때") {
            mongoTemplate.remove(Query(), Facility::class.java)

            When("requireOwner를 호출하면") {
                Then("SlotFacilityNotFoundException을 던진다") {
                    shouldThrow<SlotFacilityNotFoundException> {
                        facilityOwnershipGateway.requireOwner("FAC-MISSING", 1L)
                    }
                }
            }
        }

        Given("소유자가 지정되지 않은(ownerUserId=null) 시설이 존재할 때") {
            mongoTemplate.remove(Query(), Facility::class.java)
            seedFacility(id = "FAC-NOOWNER", ownerUserId = null)

            When("임의 userId로 requireOwner를 호출하면") {
                Then("UnauthorizedFacilityAccessException을 던진다") {
                    shouldThrow<UnauthorizedFacilityAccessException> {
                        facilityOwnershipGateway.requireOwner("FAC-NOOWNER", 1L)
                    }
                }
            }
        }
    }
}

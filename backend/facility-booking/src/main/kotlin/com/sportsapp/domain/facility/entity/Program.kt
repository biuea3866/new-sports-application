package com.sportsapp.domain.facility.entity

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.facility.exception.InvalidProgramException
import com.sportsapp.domain.facility.exception.UnauthorizedProgramAccessException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

/**
 * 시설상품(program) 애그리거트 (BE-59, TDD Detail Design "Program").
 *
 * facility 산하 PT·클래스 등 강습형 상품 메타를 표현한다. 회차 예약·결제는 기존 booking
 * `Slot`(programId 참조) 경로를 재사용하며, Program 자신은 booking 도메인을 참조하지 않는다
 * (domain 컨텍스트 교차 참조 금지, ArchUnit `AggregateAndUseCaseRulesTest`).
 */
@Entity
@Table(name = "programs")
class Program private constructor(
    @Column(name = "facility_id", nullable = false)
    val facilityId: String,

    @Column(name = "owner_user_id", nullable = false)
    val ownerUserId: Long,

    @Column(name = "name", nullable = false, length = 200)
    val name: String,

    @Column(name = "description", length = 2000)
    val description: String?,

    @Column(name = "price", nullable = false)
    val price: BigDecimal,

    @Column(name = "capacity", nullable = false)
    val capacity: Int,

    @Column(name = "duration_minutes", nullable = false)
    val durationMinutes: Int,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    fun isOwnedBy(userId: Long): Boolean = ownerUserId == userId

    fun requireOwnedBy(userId: Long) {
        if (!isOwnedBy(userId)) throw UnauthorizedProgramAccessException(id, userId)
    }

    companion object {
        fun create(
            facilityId: String,
            ownerUserId: Long,
            name: String,
            description: String?,
            price: BigDecimal,
            capacity: Int,
            durationMinutes: Int,
        ): Program {
            requireValidFacilityId(facilityId)
            requireValidName(name)
            requireValidPrice(price)
            requireValidCapacity(capacity)
            requireValidDurationMinutes(durationMinutes)
            return Program(
                facilityId = facilityId,
                ownerUserId = ownerUserId,
                name = name,
                description = description,
                price = price,
                capacity = capacity,
                durationMinutes = durationMinutes,
            )
        }

        // ThrowsCount(임계 2) 해소를 위해 불변조건마다 단일 책임 검증 메서드로 분리했다.
        // 각 메서드는 기존과 동일한 조건·메시지·평가 순서를 유지한다(동작 변경 없음).
        private fun requireValidFacilityId(facilityId: String) {
            if (facilityId.isBlank()) throw InvalidProgramException("facilityId must not be blank")
        }

        private fun requireValidName(name: String) {
            if (name.isBlank()) throw InvalidProgramException("name must not be blank")
        }

        private fun requireValidPrice(price: BigDecimal) {
            if (price < BigDecimal.ZERO) throw InvalidProgramException("price must not be negative, got: $price")
        }

        private fun requireValidCapacity(capacity: Int) {
            if (capacity <= 0) throw InvalidProgramException("capacity must be positive, got: $capacity")
        }

        private fun requireValidDurationMinutes(durationMinutes: Int) {
            if (durationMinutes <= 0) {
                throw InvalidProgramException("durationMinutes must be positive, got: $durationMinutes")
            }
        }
    }
}

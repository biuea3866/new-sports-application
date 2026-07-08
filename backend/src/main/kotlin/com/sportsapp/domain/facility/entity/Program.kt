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
            if (facilityId.isBlank()) throw InvalidProgramException("facilityId must not be blank")
            if (name.isBlank()) throw InvalidProgramException("name must not be blank")
            if (price < BigDecimal.ZERO) throw InvalidProgramException("price must not be negative, got: $price")
            if (capacity <= 0) throw InvalidProgramException("capacity must be positive, got: $capacity")
            if (durationMinutes <= 0) {
                throw InvalidProgramException("durationMinutes must be positive, got: $durationMinutes")
            }
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
    }
}

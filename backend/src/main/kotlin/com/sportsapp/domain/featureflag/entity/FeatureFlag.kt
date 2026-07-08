package com.sportsapp.domain.featureflag.entity

import com.sportsapp.domain.common.AggregateRoot
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.featureflag.event.FeatureFlagChangedEvent
import com.sportsapp.domain.featureflag.exception.FeatureFlagStatusConflictException
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.strategy.FeatureEvaluation
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import io.hypersistence.utils.hibernate.type.json.JsonStringType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EntityListeners
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.Type
import org.springframework.data.annotation.CreatedBy
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedBy
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.ZonedDateTime

/**
 * 피처 플래그 Aggregate Root — 평가 위임·상태 전이·생성 검증을 캡슐화한다.
 *
 * `feature_flags` 테이블(V42)에 `created_by`/`updated_by`만 있고 소프트 삭제 컬럼이 없어
 * [com.sportsapp.domain.common.JpaAuditingBase]를 상속하지 않고 audit 컬럼을 직접 선언한다.
 */
@Entity
@Table(name = "feature_flags")
@EntityListeners(AuditingEntityListener::class)
class FeatureFlag private constructor(
    @Column(name = "flag_key", nullable = false, unique = true, length = 100)
    val flagKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, length = 32)
    val type: FeatureFlagType,

    initialStatus: FeatureFlagStatus,
    initialStrategy: EvaluationStrategy,
    initialDescription: String?,
) : AggregateRoot() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: FeatureFlagStatus = initialStatus
        private set

    @Type(JsonStringType::class)
    @Column(name = "strategy_config", nullable = false, columnDefinition = "TEXT")
    var strategy: EvaluationStrategy = initialStrategy
        private set

    @Column(name = "description", length = 500)
    var description: String? = initialDescription
        private set

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
        private set

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    lateinit var createdAt: ZonedDateTime
        private set

    @CreatedBy
    @Column(name = "created_by", updatable = false)
    var createdBy: Long? = null
        private set

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: ZonedDateTime
        private set

    @LastModifiedBy
    @Column(name = "updated_by")
    var updatedBy: Long? = null
        private set

    /**
     * ARCHIVED 상태는 평가 대상에서 제외한다(평가 제외 신호 = Off) — 아니면 전략 평가로 위임한다.
     */
    fun evaluate(context: FeatureContext): FeatureEvaluation {
        if (!status.isEvaluable()) return FeatureEvaluation.Off
        return strategy.evaluate(flagKey, context)
    }

    fun updateStrategy(newStrategy: EvaluationStrategy, newDescription: String?) {
        if (!status.isEvaluable()) {
            throw FeatureFlagStatusConflictException(flagKey, status)
        }
        newStrategy.validateFor(type)
        strategy = newStrategy
        description = newDescription
        registerEvent(FeatureFlagChangedEvent(aggregateId = id, flagKey = flagKey))
    }

    fun archive() {
        if (!status.canTransitTo(FeatureFlagStatus.ARCHIVED)) {
            throw FeatureFlagStatusConflictException(flagKey, status)
        }
        status = FeatureFlagStatus.ARCHIVED
        registerEvent(FeatureFlagChangedEvent(aggregateId = id, flagKey = flagKey))
    }

    fun activate() {
        if (!status.canTransitTo(FeatureFlagStatus.ACTIVE)) {
            throw FeatureFlagStatusConflictException(flagKey, status)
        }
        status = FeatureFlagStatus.ACTIVE
        registerEvent(FeatureFlagChangedEvent(aggregateId = id, flagKey = flagKey))
    }

    fun toSnapshot(): FeatureFlagSnapshot = FeatureFlagSnapshot(
        key = flagKey,
        type = type,
        status = status,
        strategy = strategy,
        description = description,
    )

    companion object {
        private val FLAG_KEY_PATTERN = Regex("^[a-z0-9]+(\\.[a-z0-9-]+)*$")

        fun create(
            flagKey: String,
            type: FeatureFlagType,
            strategy: EvaluationStrategy,
            description: String?,
        ): FeatureFlag {
            require(FLAG_KEY_PATTERN.matches(flagKey)) {
                "flagKey must match lower-case dot-separated pattern (e.g. demo.feature.hello), got '$flagKey'"
            }
            strategy.validateFor(type)
            return FeatureFlag(
                flagKey = flagKey,
                type = type,
                initialStatus = FeatureFlagStatus.ACTIVE,
                initialStrategy = strategy,
                initialDescription = description,
            ).apply {
                registerEvent(FeatureFlagChangedEvent(aggregateId = id, flagKey = flagKey))
            }
        }
    }
}

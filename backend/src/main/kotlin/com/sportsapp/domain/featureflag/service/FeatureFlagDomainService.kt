package com.sportsapp.domain.featureflag.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.featureflag.dto.ActivateFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.ArchiveFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.CreateFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.GetAuditLogsCommand
import com.sportsapp.domain.featureflag.dto.UpdateFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import com.sportsapp.domain.featureflag.entity.FeatureFlagChangeType
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.exception.DuplicateFeatureFlagKeyException
import com.sportsapp.domain.featureflag.exception.FeatureFlagNotFoundException
import com.sportsapp.domain.featureflag.gateway.FeatureFlagCacheStore
import com.sportsapp.domain.featureflag.gateway.FeatureFlagChangeBroadcaster
import com.sportsapp.domain.featureflag.repository.FeatureFlagAuditLogRepository
import com.sportsapp.domain.featureflag.repository.FeatureFlagRepository
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service

/**
 * 피처 플래그 CRUD·감사·전파 오케스트레이션 (BE-04).
 *
 * UseCase 는 이 서비스만 주입한다 — Repository/Gateway/Publisher 직접 주입 금지.
 */
@Service
class FeatureFlagDomainService(
    private val featureFlagRepository: FeatureFlagRepository,
    private val featureFlagAuditLogRepository: FeatureFlagAuditLogRepository,
    private val featureFlagCacheStore: FeatureFlagCacheStore,
    private val featureFlagChangeBroadcaster: FeatureFlagChangeBroadcaster,
    private val domainEventPublisher: DomainEventPublisher,
) {

    fun create(command: CreateFeatureFlagCommand): FeatureFlag {
        if (featureFlagRepository.existsByKey(command.flagKey)) {
            throw DuplicateFeatureFlagKeyException(command.flagKey)
        }
        val flag = FeatureFlag.create(command.flagKey, command.type, command.strategy, command.description)
        val saved = featureFlagRepository.save(flag)
        recordAudit(FeatureFlagChangeType.CREATED, command.actorUserId, before = null, after = saved.toSnapshot())
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    fun update(command: UpdateFeatureFlagCommand): FeatureFlag {
        val flag = getByKey(command.key)
        val before = flag.toSnapshot()
        flag.updateStrategy(command.strategy, command.description)
        val saved = featureFlagRepository.save(flag)
        recordAudit(FeatureFlagChangeType.UPDATED, command.actorUserId, before, saved.toSnapshot())
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    fun archive(command: ArchiveFeatureFlagCommand): FeatureFlag {
        val flag = getByKey(command.key)
        val before = flag.toSnapshot()
        flag.archive()
        val saved = featureFlagRepository.save(flag)
        recordAudit(FeatureFlagChangeType.ARCHIVED, command.actorUserId, before, saved.toSnapshot())
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    fun activate(command: ActivateFeatureFlagCommand): FeatureFlag {
        val flag = getByKey(command.key)
        val before = flag.toSnapshot()
        flag.activate()
        val saved = featureFlagRepository.save(flag)
        recordAudit(FeatureFlagChangeType.ACTIVATED, command.actorUserId, before, saved.toSnapshot())
        domainEventPublisher.publishAll(saved.pullDomainEvents())
        return saved
    }

    fun getByKey(key: String): FeatureFlag =
        featureFlagRepository.findByKey(key) ?: throw FeatureFlagNotFoundException(key)

    fun findAll(status: FeatureFlagStatus?, type: FeatureFlagType?): List<FeatureFlag> =
        featureFlagRepository.findAll(status, type)

    fun getAuditLogs(command: GetAuditLogsCommand): Page<FeatureFlagAuditLog> =
        featureFlagAuditLogRepository.findByFlagKey(command.key, command.pageable)

    fun propagate(key: String) {
        val flag = getByKey(key)
        featureFlagCacheStore.put(flag.toSnapshot())
        featureFlagChangeBroadcaster.broadcast(key)
    }

    private fun recordAudit(
        changeType: FeatureFlagChangeType,
        actorUserId: Long,
        before: FeatureFlagSnapshot?,
        after: FeatureFlagSnapshot,
    ) {
        featureFlagAuditLogRepository.save(FeatureFlagAuditLog.create(changeType, actorUserId, before, after))
    }
}

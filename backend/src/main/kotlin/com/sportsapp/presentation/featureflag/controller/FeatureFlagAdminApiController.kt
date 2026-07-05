package com.sportsapp.presentation.featureflag.controller

import com.sportsapp.application.featureflag.dto.FeatureFlagResponse
import com.sportsapp.application.featureflag.dto.ListFeatureFlagAuditLogsResponse
import com.sportsapp.application.featureflag.usecase.ActivateFeatureFlagUseCase
import com.sportsapp.application.featureflag.usecase.ArchiveFeatureFlagUseCase
import com.sportsapp.application.featureflag.usecase.CreateFeatureFlagUseCase
import com.sportsapp.application.featureflag.usecase.GetFeatureFlagAuditLogsUseCase
import com.sportsapp.application.featureflag.usecase.GetFeatureFlagUseCase
import com.sportsapp.application.featureflag.usecase.ListFeatureFlagsUseCase
import com.sportsapp.application.featureflag.usecase.UpdateFeatureFlagUseCase
import com.sportsapp.domain.featureflag.dto.ActivateFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.ArchiveFeatureFlagCommand
import com.sportsapp.domain.featureflag.dto.GetAuditLogsCommand
import com.sportsapp.domain.featureflag.dto.ListFeatureFlagsCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.featureflag.dto.CreateFeatureFlagRequest
import com.sportsapp.presentation.featureflag.dto.UpdateFeatureFlagRequest
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 피처 플래그 운영자용 관리 REST API (FR-7·FR-8, BE-08).
 *
 * base `/admin/feature-flags`는 [com.sportsapp.infrastructure.security.SecurityConfig]의
 * admin 하위 전체 경로 `hasRole("ADMIN")` 규칙이 이미 커버한다 — 별도 인가 설정 불필요.
 * actor는 SecurityContext principal(`UserPrincipal.id`)에서 획득해 Command에 주입한다.
 */
@RestController
@RequestMapping("/admin/feature-flags")
class FeatureFlagAdminApiController(
    private val createFeatureFlagUseCase: CreateFeatureFlagUseCase,
    private val updateFeatureFlagUseCase: UpdateFeatureFlagUseCase,
    private val archiveFeatureFlagUseCase: ArchiveFeatureFlagUseCase,
    private val activateFeatureFlagUseCase: ActivateFeatureFlagUseCase,
    private val getFeatureFlagUseCase: GetFeatureFlagUseCase,
    private val listFeatureFlagsUseCase: ListFeatureFlagsUseCase,
    private val getFeatureFlagAuditLogsUseCase: GetFeatureFlagAuditLogsUseCase,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateFeatureFlagRequest,
    ): FeatureFlagResponse = createFeatureFlagUseCase.execute(request.toCommand(principal.id))

    @GetMapping
    fun list(
        @RequestParam(required = false) status: FeatureFlagStatus?,
        @RequestParam(required = false) type: FeatureFlagType?,
    ): List<FeatureFlagResponse> =
        listFeatureFlagsUseCase.execute(ListFeatureFlagsCommand(status = status, type = type))

    @GetMapping("/{key}")
    fun getOne(@PathVariable key: String): FeatureFlagResponse = getFeatureFlagUseCase.execute(key)

    @PutMapping("/{key}")
    fun update(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable key: String,
        @RequestBody request: UpdateFeatureFlagRequest,
    ): FeatureFlagResponse = updateFeatureFlagUseCase.execute(request.toCommand(key, principal.id))

    @PostMapping("/{key}/archive")
    fun archive(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable key: String,
    ): FeatureFlagResponse =
        archiveFeatureFlagUseCase.execute(ArchiveFeatureFlagCommand(key = key, actorUserId = principal.id))

    @PostMapping("/{key}/activate")
    fun activate(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable key: String,
    ): FeatureFlagResponse =
        activateFeatureFlagUseCase.execute(ActivateFeatureFlagCommand(key = key, actorUserId = principal.id))

    @GetMapping("/{key}/audit-logs")
    fun auditLogs(
        @PathVariable key: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ListFeatureFlagAuditLogsResponse {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))
        return getFeatureFlagAuditLogsUseCase.execute(GetAuditLogsCommand(key = key, pageable = pageable))
    }
}

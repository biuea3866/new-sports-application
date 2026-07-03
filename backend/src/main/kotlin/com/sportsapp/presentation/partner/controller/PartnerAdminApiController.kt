package com.sportsapp.presentation.partner.controller

import com.sportsapp.application.partner.audit.ListPartnerAuditLogsCommand
import com.sportsapp.application.partner.audit.ListPartnerAuditLogsUseCase
import com.sportsapp.application.partner.audit.PartnerAuditLogResponse
import com.sportsapp.application.partner.dto.ChangePartnerStatusResponse
import com.sportsapp.application.partner.dto.CreatePartnerResponse
import com.sportsapp.application.partner.dto.ReissueApiKeyCommand
import com.sportsapp.application.partner.dto.ReissueApiKeyResponse
import com.sportsapp.application.partner.dto.RevokeApiKeyCommand
import com.sportsapp.application.partner.usecase.ChangePartnerStatusUseCase
import com.sportsapp.application.partner.usecase.CreatePartnerUseCase
import com.sportsapp.application.partner.usecase.ReissueApiKeyUseCase
import com.sportsapp.application.partner.usecase.RevokeApiKeyUseCase
import com.sportsapp.presentation.partner.dto.request.ChangePartnerStatusRequest
import com.sportsapp.presentation.partner.dto.request.CreatePartnerRequest
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
@RequestMapping("/api/admin/partners")
@PreAuthorize("hasRole('ADMIN')")
class PartnerAdminApiController(
    private val createPartnerUseCase: CreatePartnerUseCase,
    private val reissueApiKeyUseCase: ReissueApiKeyUseCase,
    private val revokeApiKeyUseCase: RevokeApiKeyUseCase,
    private val changePartnerStatusUseCase: ChangePartnerStatusUseCase,
    private val listPartnerAuditLogsUseCase: ListPartnerAuditLogsUseCase,
) {
    @PostMapping
    fun createPartner(
        @Valid @RequestBody request: CreatePartnerRequest,
    ): ResponseEntity<CreatePartnerResponse> {
        val response = createPartnerUseCase.execute(request.toCommand())
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{partnerId}/api-keys")
    fun reissueApiKey(
        @PathVariable partnerId: Long,
    ): ResponseEntity<ReissueApiKeyResponse> {
        val response = reissueApiKeyUseCase.execute(ReissueApiKeyCommand(partnerId = partnerId))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @DeleteMapping("/{partnerId}/api-keys/{keyId}")
    fun revokeApiKey(
        @PathVariable partnerId: Long,
        @PathVariable keyId: Long,
    ): ResponseEntity<Void> {
        revokeApiKeyUseCase.execute(RevokeApiKeyCommand(partnerId = partnerId, keyId = keyId))
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{partnerId}/status")
    fun changeStatus(
        @PathVariable partnerId: Long,
        @Valid @RequestBody request: ChangePartnerStatusRequest,
    ): ResponseEntity<ChangePartnerStatusResponse> {
        val response = changePartnerStatusUseCase.execute(request.toCommand(partnerId))
        return ResponseEntity.ok(response)
    }

    @GetMapping("/{partnerId}/audit-logs")
    fun listAuditLogs(
        @PathVariable partnerId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: ZonedDateTime?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: ZonedDateTime?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<PartnerAuditLogResponse>> {
        val command = ListPartnerAuditLogsCommand.of(partnerId, from, to, pageable)
        return ResponseEntity.ok(listPartnerAuditLogsUseCase.execute(command))
    }
}

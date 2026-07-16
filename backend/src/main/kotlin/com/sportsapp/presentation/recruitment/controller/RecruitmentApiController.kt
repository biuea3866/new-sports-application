package com.sportsapp.presentation.recruitment.controller

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.ApplyRecruitmentResult
import com.sportsapp.application.recruitment.dto.CancelRecruitmentCommand
import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.application.recruitment.usecase.ApplyRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.CancelRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.CreateRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.GetRecruitmentUseCase
import com.sportsapp.application.recruitment.usecase.ListApplicationsUseCase
import com.sportsapp.application.recruitment.usecase.ListRecruitmentsUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.recruitment.dto.request.ApplyRecruitmentRequest
import com.sportsapp.presentation.recruitment.dto.request.CreateRecruitmentRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/recruitments")
class RecruitmentApiController(
    private val createRecruitmentUseCase: CreateRecruitmentUseCase,
    private val listRecruitmentsUseCase: ListRecruitmentsUseCase,
    private val getRecruitmentUseCase: GetRecruitmentUseCase,
    private val listApplicationsUseCase: ListApplicationsUseCase,
    private val applyRecruitmentUseCase: ApplyRecruitmentUseCase,
    private val cancelRecruitmentUseCase: CancelRecruitmentUseCase,
) {
    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateRecruitmentRequest,
    ): ResponseEntity<RecruitmentResponse> {
        val response = createRecruitmentUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) communityId: Long?,
    ): ResponseEntity<List<RecruitmentResponse>> =
        ResponseEntity.ok(listRecruitmentsUseCase.execute(communityId, principal?.id))

    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal principal: UserPrincipal?,
        @PathVariable id: Long,
    ): ResponseEntity<RecruitmentResponse> =
        ResponseEntity.ok(getRecruitmentUseCase.execute(id, principal?.id))

    @GetMapping("/{id}/applications")
    fun listApplications(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<List<ApplicationResponse>> =
        ResponseEntity.ok(listApplicationsUseCase.execute(id, principal.id))

    @PostMapping("/{id}/applications")
    fun apply(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
        @RequestBody request: ApplyRecruitmentRequest,
    ): ResponseEntity<ApplyRecruitmentResult> {
        val result = applyRecruitmentUseCase.execute(request.toCommand(id, principal.id))
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(result)
    }

    @PostMapping("/{id}/cancel")
    fun cancel(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<RecruitmentResponse> {
        val response = cancelRecruitmentUseCase.execute(
            CancelRecruitmentCommand(recruitmentId = id, recruiterUserId = principal.id),
        )
        return ResponseEntity.ok(response)
    }
}

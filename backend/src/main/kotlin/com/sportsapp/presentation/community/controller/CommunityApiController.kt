package com.sportsapp.presentation.community.controller

import com.sportsapp.application.community.dto.ApproveMemberCommand
import com.sportsapp.application.community.dto.CommunityMemberResponse
import com.sportsapp.application.community.dto.CommunityResponse
import com.sportsapp.application.community.dto.JoinCommunityCommand
import com.sportsapp.application.community.dto.KickMemberCommand
import com.sportsapp.application.community.dto.LeaveCommunityCommand
import com.sportsapp.application.community.usecase.ApproveMemberUseCase
import com.sportsapp.application.community.usecase.CreateCommunityUseCase
import com.sportsapp.application.community.usecase.GetCommunityUseCase
import com.sportsapp.application.community.usecase.JoinCommunityUseCase
import com.sportsapp.application.community.usecase.KickMemberUseCase
import com.sportsapp.application.community.usecase.LeaveCommunityUseCase
import com.sportsapp.application.community.usecase.ListCommunityMembersUseCase
import com.sportsapp.application.community.usecase.ListMyCommunitiesUseCase
import com.sportsapp.application.community.usecase.ListPublicCommunitiesUseCase
import com.sportsapp.application.community.usecase.TransferHostUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.community.dto.request.CreateCommunityRequest
import com.sportsapp.presentation.community.dto.request.TransferHostRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val COMMUNITY_ENABLED_PROPERTY = "chat.community.enabled"

/**
 * 커뮤니티 REST 계약 (TDD "REST API 계약", FR-1/2/3/13). `chat.community.enabled=false`면
 * 빈 자체가 등록되지 않아 communities 하위 전체 경로가 404(즉시 롤백 지점, Release Scenario).
 */
@RestController
@RequestMapping("/communities")
@ConditionalOnProperty(name = [COMMUNITY_ENABLED_PROPERTY], havingValue = "true", matchIfMissing = false)
class CommunityApiController(
    private val createCommunityUseCase: CreateCommunityUseCase,
    private val joinCommunityUseCase: JoinCommunityUseCase,
    private val approveMemberUseCase: ApproveMemberUseCase,
    private val kickMemberUseCase: KickMemberUseCase,
    private val transferHostUseCase: TransferHostUseCase,
    private val leaveCommunityUseCase: LeaveCommunityUseCase,
    private val getCommunityUseCase: GetCommunityUseCase,
    private val listPublicCommunitiesUseCase: ListPublicCommunitiesUseCase,
    private val listCommunityMembersUseCase: ListCommunityMembersUseCase,
    private val listMyCommunitiesUseCase: ListMyCommunitiesUseCase,
) {

    @PostMapping
    fun createCommunity(
        @RequestBody request: CreateCommunityRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<CommunityResponse> {
        val response = createCommunityUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.ok(response)
    }

    @GetMapping
    fun listPublicCommunities(
        @RequestParam(required = false) keyword: String?,
    ): ResponseEntity<List<CommunityResponse>> =
        ResponseEntity.ok(listPublicCommunitiesUseCase.execute(keyword))

    @GetMapping("/me")
    fun listMyCommunities(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<List<CommunityResponse>> =
        ResponseEntity.ok(listMyCommunitiesUseCase.execute(principal.id))

    @GetMapping("/{id}")
    fun getCommunity(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<CommunityResponse> =
        ResponseEntity.ok(getCommunityUseCase.execute(communityId = id, requesterId = principal.id))

    @GetMapping("/{id}/members")
    fun listCommunityMembers(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<List<CommunityMemberResponse>> =
        ResponseEntity.ok(listCommunityMembersUseCase.execute(communityId = id, requesterId = principal.id))

    @PostMapping("/{id}/join")
    fun joinCommunity(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<CommunityMemberResponse> {
        val response = joinCommunityUseCase.execute(JoinCommunityCommand(communityId = id, userId = principal.id))
        return ResponseEntity.ok(response)
    }

    @PostMapping("/{id}/members/{userId}/approve")
    fun approveMember(
        @PathVariable id: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<CommunityMemberResponse> {
        val command = ApproveMemberCommand(communityId = id, requesterId = principal.id, targetUserId = userId)
        return ResponseEntity.ok(approveMemberUseCase.execute(command))
    }

    @PostMapping("/{id}/members/{userId}/kick")
    fun kickMember(
        @PathVariable id: Long,
        @PathVariable userId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        kickMemberUseCase.execute(KickMemberCommand(communityId = id, requesterId = principal.id, targetUserId = userId))
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/host/transfer")
    fun transferHost(
        @PathVariable id: Long,
        @RequestBody request: TransferHostRequest,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        transferHostUseCase.execute(request.toCommand(communityId = id, requesterId = principal.id))
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{id}/members/me")
    fun leaveCommunity(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        leaveCommunityUseCase.execute(LeaveCommunityCommand(communityId = id, userId = principal.id))
        return ResponseEntity.noContent().build()
    }
}

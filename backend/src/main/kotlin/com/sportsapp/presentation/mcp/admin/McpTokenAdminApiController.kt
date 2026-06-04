package com.sportsapp.presentation.mcp.admin

import com.sportsapp.application.mcp.IssueMcpTokenResponse
import com.sportsapp.application.mcp.IssueMcpTokenUseCase
import com.sportsapp.application.mcp.ListMcpTokensResponse
import com.sportsapp.application.mcp.ListMcpTokensUseCase
import com.sportsapp.application.mcp.RevokeMcpTokenCommand
import com.sportsapp.application.mcp.RevokeMcpTokenUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/mcp/tokens")
class McpTokenAdminApiController(
    private val issueMcpTokenUseCase: IssueMcpTokenUseCase,
    private val listMcpTokensUseCase: ListMcpTokensUseCase,
    private val revokeMcpTokenUseCase: RevokeMcpTokenUseCase,
) {
    @PostMapping
    fun issueToken(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: IssueMcpTokenRequest,
    ): ResponseEntity<IssueMcpTokenResponse> {
        val response = issueMcpTokenUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @GetMapping
    fun listTokens(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<ListMcpTokensResponse> {
        val response = listMcpTokensUseCase.execute(userId = principal.id)
        return ResponseEntity.ok(response)
    }

    @DeleteMapping("/{tokenId}")
    fun revokeToken(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable tokenId: Long,
    ): ResponseEntity<Void> {
        revokeMcpTokenUseCase.execute(RevokeMcpTokenCommand(tokenId = tokenId, requesterId = principal.id))
        return ResponseEntity.noContent().build()
    }
}

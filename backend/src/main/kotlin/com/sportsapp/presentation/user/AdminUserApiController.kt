package com.sportsapp.presentation.user

import com.sportsapp.application.user.AssignRoleCommand
import com.sportsapp.application.user.AssignRoleUseCase
import com.sportsapp.application.user.ListUsersCommand
import com.sportsapp.application.user.ListUsersResponse
import com.sportsapp.application.user.ListUsersUseCase
import com.sportsapp.application.user.RevokeRoleCommand
import com.sportsapp.application.user.RevokeRoleUseCase
import com.sportsapp.domain.user.UserPrincipal
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/users")
@PreAuthorize("hasRole('ADMIN')")
class AdminUserApiController(
    private val assignRoleUseCase: AssignRoleUseCase,
    private val revokeRoleUseCase: RevokeRoleUseCase,
    private val listUsersUseCase: ListUsersUseCase,
) {
    @GetMapping
    fun listUsers(
        @RequestParam(required = false) emailKeyword: String?,
        @RequestParam(required = false) roleName: String?,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<ListUsersResponse>> =
        ResponseEntity.ok(
            listUsersUseCase.execute(
                ListUsersCommand(
                    emailKeyword = emailKeyword,
                    roleName = roleName,
                    pageable = pageable,
                ),
            ),
        )

    @PostMapping("/{userId}/roles/{roleName}")
    fun assignRole(
        @PathVariable userId: Long,
        @PathVariable roleName: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        assignRoleUseCase.execute(
            AssignRoleCommand(
                adminId = principal.id,
                userId = userId,
                roleName = roleName,
            ),
        )
        return ResponseEntity.ok().build()
    }

    @DeleteMapping("/{userId}/roles/{roleName}")
    fun revokeRole(
        @PathVariable userId: Long,
        @PathVariable roleName: String,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        revokeRoleUseCase.execute(
            RevokeRoleCommand(
                adminId = principal.id,
                userId = userId,
                roleName = roleName,
            ),
        )
        return ResponseEntity.ok().build()
    }
}

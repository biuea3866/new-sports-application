package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.RefreshCommand
import com.sportsapp.domain.user.service.AuthDomainService
import com.sportsapp.domain.user.vo.TokenPair
import org.springframework.stereotype.Service

@Service
class RefreshUseCase(
    private val authDomainService: AuthDomainService,
) {
    fun execute(command: RefreshCommand): TokenPair =
        authDomainService.refresh(command.refreshToken)
}

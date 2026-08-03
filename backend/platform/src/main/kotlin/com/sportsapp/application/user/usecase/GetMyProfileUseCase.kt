package com.sportsapp.application.user.usecase

import com.sportsapp.application.user.dto.GetMyProfileCommand
import com.sportsapp.application.user.dto.GetMyProfileResponse
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 내 프로필 조회. 응답 변환을 트랜잭션 안에서 끝낸다 — `open-in-view: false` 라
 * 엔티티를 컨트롤러까지 들고 가면 지연 로딩 접근이 터진다.
 */
@Service
class GetMyProfileUseCase(
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetMyProfileCommand): GetMyProfileResponse =
        GetMyProfileResponse.of(userDomainService.findById(command.userId))
}

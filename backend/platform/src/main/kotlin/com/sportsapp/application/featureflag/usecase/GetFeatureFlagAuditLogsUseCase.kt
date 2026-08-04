package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.dto.ListFeatureFlagAuditLogsResponse
import com.sportsapp.domain.featureflag.dto.GetAuditLogsCommand
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 변경자를 내부 PK(`actorUserId`) 노출 대신 표시 이름으로 보여주기 위한 조합.
 * featureflag 도메인은 user를 모른 채로 두고, 이 UseCase(application 레이어)가
 * [UserDomainService]로 조회해 응답에 싣는다 (post 컨텍스트의 `GetPostUseCase` 선례와 동일 구조).
 */
@Service
class GetFeatureFlagAuditLogsUseCase(
    private val featureFlagDomainService: FeatureFlagDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: GetAuditLogsCommand): ListFeatureFlagAuditLogsResponse {
        val page = featureFlagDomainService.getAuditLogs(command)
        val actorDisplayNames = userDomainService.findDisplayNamesBy(page.content.map { it.actorUserId })
        return ListFeatureFlagAuditLogsResponse.of(page, actorDisplayNames)
    }
}

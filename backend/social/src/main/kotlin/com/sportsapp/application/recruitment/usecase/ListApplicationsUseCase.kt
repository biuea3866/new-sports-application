package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.RecruitmentApplicantResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 개설자용 신청 목록 조회. 개설자 검증은 RecruitmentDomainService.findApplications 내부(Recruitment.requireRecruiter)에서 수행한다.
 *
 * 신청자 표시 이름은 user 컨텍스트가 소유한다 — recruitment 도메인이 user 를 참조하지 않도록,
 * 두 컨텍스트를 모두 아는 이 application 레이어가 [UserDomainService] 로 한 번에 조회해 조합한다
 * (신청자 수만큼 단건 조회하는 N+1 을 만들지 않는다).
 */
@Service
class ListApplicationsUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(recruitmentId: Long, requesterUserId: Long): List<RecruitmentApplicantResponse> {
        val applications = recruitmentDomainService.findApplications(recruitmentId, requesterUserId)
        val applicantNames = userDomainService.findDisplayNamesBy(applications.map { it.applicantUserId })
        return applications.map { RecruitmentApplicantResponse.of(it, applicantNames.of(it.applicantUserId)) }
    }
}

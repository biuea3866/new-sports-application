package sportsapp.testkit.presentation.exception

import com.sportsapp.domain.common.ErrorStatus
import org.springframework.http.ProblemDetail
import java.net.URI

/**
 * [W1-01b 리뷰 ①] [GlobalExceptionHandler] 가 사용하는 `ProblemDetail` 빌더 — payment·commerce·
 * facility-booking 3모듈 복제본을 `common`의 testFixtures 로 통합한 버전이다. bootstrap 의
 * `com.sportsapp.presentation.exception.ProblemDetailBuilder`(운영 배포 원본)와 동일 계약이다.
 *
 * 패키지를 `sportsapp.testkit.presentation.exception`(`com.sportsapp` 밖)으로 분리한 이유는
 * [GlobalExceptionHandler] KDoc 참고 — bootstrap 자신의 main 원본과 FQCN 충돌(클래스패스 섀도잉)뿐
 * 아니라, bootstrap 컴포넌트 스캔 오염까지 함께 차단하기 위함이다.
 */
object ProblemDetailBuilder {

    fun build(status: ErrorStatus, code: String, detail: String): ProblemDetail {
        val problemDetail = ProblemDetail.forStatus(status.httpStatus)
        problemDetail.type = URI.create("https://errors.sports-application/${code.lowercase().replace('_', '-')}")
        problemDetail.title = code.split('_').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
        problemDetail.detail = detail
        problemDetail.setProperty("code", code)
        return problemDetail
    }
}

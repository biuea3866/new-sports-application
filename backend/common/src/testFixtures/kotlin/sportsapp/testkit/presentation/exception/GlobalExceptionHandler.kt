package sportsapp.testkit.presentation.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authorization.AuthorizationDeniedException
import jakarta.validation.ConstraintViolationException
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.QueryTimeoutException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * [W1-01b 리뷰 ①] payment·commerce·facility-booking 이 각자 `src/test`에 복제해 쓰던 standalone
 * MockMvc 컨트롤러 테스트(예: PaymentApiControllerTest·CartApiControllerTest·BookingApiControllerTest)
 * 전용 테스트 하네스를 `common`의 `testFixtures`로 통합한 버전이다. `bootstrap`의 main 소스셋에 있는
 * `com.sportsapp.presentation.exception.GlobalExceptionHandler`(운영 배포 원본)와 매핑 로직이 동일하다.
 *
 * 원본은 bootstrap 의 main 소스셋에 있어 다른 모듈이 참조할 수 없다(모듈 의존 방향상 payment/commerce/
 * facility-booking → bootstrap 은 성립하지 않는다 — bootstrap 이 이들을 의존하는 방향이다). 이 클래스를
 * 쓰는 컨트롤러 테스트들은 `MockMvcBuilders.standaloneSetup(controller, GlobalExceptionHandler())` 로
 * 컨트롤러 어드바이스를 직접 구성하므로, Spring 컨텍스트 기동 없이 testFixtures 소스셋에만 존재하면 된다.
 *
 * **패키지를 원본과 다르게, 그리고 `com.sportsapp.**` 밖으로(`sportsapp.testkit.presentation.exception`)
 * 가져간 이유** — 기존 3개 모듈 로컬 복제본은 각자의 테스트 클래스패스에만 존재해 원본과 부딪히지
 * 않았지만, 이 클래스는 `common`의 testFixtures 아티팩트로 이동한다. `bootstrap`도
 * `testImplementation(testFixtures(project(":common")))`로 `common`의 testFixtures 를 이미 참조하므로
 * (`BaseIntegrationTest` 등을 쓰기 위해), 원본과 동일 패키지·클래스명
 * (`com.sportsapp.presentation.exception.GlobalExceptionHandler`)을 그대로 썼다면 bootstrap 의 test
 * 런타임 클래스패스에 **동일 FQCN 클래스가 두 개**(bootstrap 자신의 main 출력 + common-testFixtures
 * 아티팩트) 존재하게 되어, 클래스패스 순서에 따라 어느 쪽이 로드될지 결정되는 조용한 섀도잉 위험이
 * 생긴다(이 버전은 goods 핸들러가 빠져 있어 원본과 동작이 달라, 잘못 로드되면 bootstrap 자신의 검증을
 * 무력화할 수 있다).
 *
 * 단순히 `com.sportsapp.testkit.**`처럼 하위 패키지만 바꾸는 것으로는 **부족하다** — 이 클래스는
 * `@RestControllerAdvice`(메타 애노테이션상 `@Component`)이고, `bootstrap`의 `SportsApplication`
 * (`@SpringBootApplication`)의 컴포넌트 스캔 베이스가 `com.sportsapp`이므로 `com.sportsapp` 아래
 * 있는 한 FQCN이 달라도 **bootstrap 풀부팅 컨텍스트에 두 번째 빈으로 스캔되어 등록**된다(빈 이름이
 * `globalExceptionHandler`로 원본과 충돌해 `ConflictingBeanDefinitionException`을 일으키거나, 이름
 * 충돌을 피해도 원본과 동작이 다른 advice가 조용히 함께 등록되는 문제가 남는다). 그래서 패키지 루트를
 * `com.sportsapp` **밖으로**(`sportsapp.testkit.*`) 빼 컴포넌트 스캔 자체에서 제외한다 — 이 프로젝트는
 * 아직 전용 패키지 이동·`@ComponentScan.Filter` 둘 다 가능하지만, 별도 아티팩트(testFixtures)이자
 * 스캔 베이스 밖 패키지라 빈 공급을 더 단순하게 막을 수 있는 후자를 택했다.
 *
 * goods 전용 핸들러(`LimitedDropTooEarlyException`)는 이 공용 버전에서 **제외**한다 — commerce 를 제외한
 * payment·facility-booking 모듈에는 goods 도메인이 없고, commerce 자신의 컨트롤러 테스트도 이 예외를
 * HTTP 레이어에서 검증하지 않는다(BusinessException 상위 핸들러로 폴백되는 원본과 동작 차이는 없다 —
 * `LimitedDropTooEarlyException` 은 `BusinessException` 의 하위 타입이라 `status`/`errorCode`/`message`
 * 는 동일하게 매핑되고, 원본에만 추가되는 `openAt` 필드만 응답 본문에서 빠진다).
 *
 * 원본(bootstrap main)이 변경되면 이 공용 복제본도 함께 갱신해야 한다 — 완료 보고 "미해결·후속" 참고
 * (2단계 물리 분리 시 서비스별 실제 소유 GlobalExceptionHandler 로 전환).
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(exception: BusinessException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = exception.status,
            code = exception.errorCode,
            detail = exception.message
        )
        return ResponseEntity.status(exception.status.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(AuthorizationDeniedException::class)
    fun handleAuthorizationDeniedException(exception: AuthorizationDeniedException): ResponseEntity<ProblemDetail> {
        logger.debug("Authorization denied: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.FORBIDDEN,
            code = "FORBIDDEN",
            detail = "Access denied"
        )
        return ResponseEntity.status(ErrorStatus.FORBIDDEN.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(exception: ConstraintViolationException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = "Request parameter validation failed"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "VALIDATION_ERROR",
            detail = "Request validation failed"
        )
        val fieldErrors = exception.bindingResult.fieldErrors.map { fieldError ->
            mapOf(
                "field" to fieldError.field,
                "message" to (fieldError.defaultMessage ?: "invalid value")
            )
        }
        problemDetail.setProperty("fieldErrors", fieldErrors)
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleMissingRequestHeaderException(exception: MissingRequestHeaderException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "MISSING_REQUEST_HEADER",
            detail = "Required header is missing: ${exception.headerName}"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingServletRequestParameterException(
        exception: MissingServletRequestParameterException,
    ): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "MISSING_REQUEST_PARAMETER",
            detail = "Required request parameter is missing: ${exception.parameterName}"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleMethodArgumentTypeMismatchException(
        exception: MethodArgumentTypeMismatchException,
    ): ResponseEntity<ProblemDetail> {
        val paramName = exception.name
        val invalidValue = exception.value
        val requiredType = exception.requiredType?.simpleName ?: "unknown"
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "INVALID_ENUM_VALUE",
            detail = "Invalid value '$invalidValue' for parameter '$paramName'. Expected type: $requiredType",
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMessageNotReadableException(exception: HttpMessageNotReadableException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "MALFORMED_REQUEST_BODY",
            detail = "Request body is malformed or contains an invalid value"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(exception: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.BAD_REQUEST,
            code = "BAD_REQUEST",
            detail = exception.message ?: "Invalid request"
        )
        return ResponseEntity.status(ErrorStatus.BAD_REQUEST.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(exception: AccessDeniedException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN)
        problemDetail.detail = exception.message ?: "Access denied"
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(problemDetail)
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLockException(exception: ObjectOptimisticLockingFailureException): ResponseEntity<ProblemDetail> {
        logger.debug("Optimistic lock conflict: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.CONFLICT,
            code = "OPTIMISTIC_LOCK_CONFLICT",
            detail = "Resource was modified concurrently. Please retry."
        )
        return ResponseEntity.status(ErrorStatus.CONFLICT.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFoundException(exception: NoResourceFoundException): ResponseEntity<ProblemDetail> {
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.NOT_FOUND,
            code = "NOT_FOUND",
            detail = "Requested resource does not exist"
        )
        return ResponseEntity.status(ErrorStatus.NOT_FOUND.httpStatus).body(problemDetail)
    }

    @ExceptionHandler(CannotCreateTransactionException::class)
    fun handleCannotCreateTransactionException(
        exception: CannotCreateTransactionException,
    ): ResponseEntity<ProblemDetail> {
        logger.warn("Connection pool exhausted while starting transaction: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.SERVICE_UNAVAILABLE,
            code = "SERVICE_UNAVAILABLE",
            detail = "Server is under heavy load. Please retry shortly."
        )
        return ResponseEntity.status(ErrorStatus.SERVICE_UNAVAILABLE.httpStatus)
            .header("Retry-After", "1")
            .body(problemDetail)
    }

    @ExceptionHandler(DataAccessResourceFailureException::class)
    fun handleDataAccessResourceFailureException(
        exception: DataAccessResourceFailureException,
    ): ResponseEntity<ProblemDetail> {
        logger.warn("Connection pool exhausted while accessing data: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.SERVICE_UNAVAILABLE,
            code = "SERVICE_UNAVAILABLE",
            detail = "Server is under heavy load. Please retry shortly."
        )
        return ResponseEntity.status(ErrorStatus.SERVICE_UNAVAILABLE.httpStatus)
            .header("Retry-After", "1")
            .body(problemDetail)
    }

    @ExceptionHandler(QueryTimeoutException::class)
    fun handleQueryTimeoutException(exception: QueryTimeoutException): ResponseEntity<ProblemDetail> {
        logger.warn("Query timed out under load: {}", exception.message)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.SERVICE_UNAVAILABLE,
            code = "SERVICE_UNAVAILABLE",
            detail = "Server is under heavy load. Please retry shortly."
        )
        return ResponseEntity.status(ErrorStatus.SERVICE_UNAVAILABLE.httpStatus)
            .header("Retry-After", "1")
            .body(problemDetail)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnknownException(exception: Exception): ResponseEntity<ProblemDetail> {
        logger.error("Unhandled exception", exception)
        val problemDetail = ProblemDetailBuilder.build(
            status = ErrorStatus.INTERNAL,
            code = "INTERNAL_ERROR",
            detail = "An unexpected error occurred"
        )
        return ResponseEntity.status(ErrorStatus.INTERNAL.httpStatus).body(problemDetail)
    }
}

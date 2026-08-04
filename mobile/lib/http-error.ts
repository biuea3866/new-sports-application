/**
 * http-error — axios 에러에서 HTTP status를 판별하는 공용 순수 유틸.
 *
 * 근거: `app/communities/[id].tsx`·`app/recruitments/[id]/applications.tsx`에 중복 정의돼
 * 있던 403 판별 로직을 게시판(A-P2)·소모임 예약(A-B1) 섹션에서도 재사용하기 위해 추출한다.
 */
import axios from 'axios';

/** 응답 status가 403(Forbidden)인 axios 에러인지 판별한다. */
export function isForbiddenError(error: Error | null | undefined): boolean {
  return axios.isAxiosError(error) && error.response?.status === 403;
}

/** 응답 status가 404(Not Found)인 axios 에러인지 판별한다. */
export function isNotFoundError(error: Error | null | undefined): boolean {
  return axios.isAxiosError(error) && error.response?.status === 404;
}

/** 응답 status가 401(Unauthorized)인 axios 에러인지 판별한다. */
export function isUnauthorizedError(error: Error | null | undefined): boolean {
  return axios.isAxiosError(error) && error.response?.status === 401;
}

/**
 * BE 에러 응답 본문 — Spring ProblemDetail.
 *
 * `ProblemDetailBuilder.build` 가 `setProperty("code", ...)` 로 담는다. Spring 의
 * `ProblemDetailJacksonMixin` 이 `getProperties()` 에 `@JsonAnyGetter` 를 붙여 **최상위 `code` 로
 * 평탄화**되는 것이 현재 동작이다 (RFC 7807 확장 멤버 규약과 일치, BE 통합 테스트가 `$.code` 로 검증).
 *
 * 전역 ObjectMapper 가 Boot 빌더 기반이 아니던 시절(PR #385 이전)에는 그 믹스인이 없어
 * `properties.code` 로 중첩됐다. 두 형태를 모두 읽는 아래 폴백은 그 시절 응답과 캐시된 오류 본문을
 * 위한 방어이며, 순서(중첩 우선)를 바꿀 필요는 없다 — 둘 중 하나만 존재한다.
 */
interface ProblemDetailBody {
  properties?: { code?: string };
  code?: string;
  detail?: string;
}

/**
 * axios 에러에서 BE 에러 코드를 꺼낸다. 화면마다 응답 모양을 추측하지 않도록 판별 지점을
 * 여기 하나로 둔다 — 없는 필드(`errorCode` 등)를 읽어 안내 분기가 죽는 사고를 막기 위한 것이다.
 */
export function extractProblemCode(error: unknown): string | undefined {
  if (!axios.isAxiosError(error)) return undefined;
  const body = error.response?.data as ProblemDetailBody | undefined;
  return body?.properties?.code ?? body?.code;
}

/** 응답 status 와 BE 에러 코드가 모두 일치하는지 판별한다. */
export function hasProblemCode(error: unknown, status: number, code: string): boolean {
  return axios.isAxiosError(error) && error.response?.status === status
    ? extractProblemCode(error) === code
    : false;
}

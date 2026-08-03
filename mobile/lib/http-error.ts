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
 * `ProblemDetailBuilder.build` 가 `setProperty("code", ...)` 로 담고,
 * `spring.mvc.problemdetails.enabled` 미설정이라 unwrap 되지 않아 실제 직렬화는
 * `properties.code` 다 (BE 통합 테스트가 `$.properties.code` 로 검증). 구 응답의
 * 최상위 `code` 형태도 방어적으로 폴백한다.
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

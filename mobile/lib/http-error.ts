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

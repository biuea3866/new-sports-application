/**
 * useCurrentUserId — 현재 로그인 사용자 id.
 *
 * 서버 프로필(GET /users/me)이 유일한 기준이다. accessToken은 메모리에만 살아 있어
 * 앱을 새로 열면 비고, 토큰을 직접 디코딩하면 화면마다 파싱이 흩어진다.
 * 프로필을 아직 받지 못했으면 0을 반환해 호출부가 조회를 게이팅할 수 있게 한다.
 */
import { useMyProfile } from './useMyProfile';

export const UNKNOWN_USER_ID = 0;

export function useCurrentUserId(): number {
  const { data } = useMyProfile();
  return data?.id ?? UNKNOWN_USER_ID;
}

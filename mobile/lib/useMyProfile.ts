/**
 * useMyProfile — GET /users/me TanStack Query 훅
 * useChangeMyNickname — PATCH /users/me/nickname mutation 훅
 *
 * 서버 상태(내 프로필)는 Query 캐시가 SSOT — 스토어에 복사하지 않는다. 닉네임 변경 성공 시
 * 프로필 캐시를 응답으로 갱신해 화면이 즉시 새 이름을 보여준다.
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { changeMyNickname, getMyProfile } from '../api/user';
import type { MyProfileResponse } from '../api/types';

export const MY_PROFILE_QUERY_KEY = ['users', 'me'] as const;

export function useMyProfile() {
  return useQuery<MyProfileResponse, Error>({
    queryKey: MY_PROFILE_QUERY_KEY,
    queryFn: getMyProfile,
  });
}

export function useChangeMyNickname() {
  const queryClient = useQueryClient();

  return useMutation<MyProfileResponse, Error, string>({
    mutationFn: (nickname: string) => changeMyNickname(nickname),
    onSuccess: (profile) => {
      queryClient.setQueryData(MY_PROFILE_QUERY_KEY, profile);
    },
  });
}

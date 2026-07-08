/**
 * useMyProfile — GET /users/me TanStack Query 훅
 */
import { useQuery } from '@tanstack/react-query';
import { getMyProfile } from '../api/user';
import type { MyProfileResponse } from '../api/types';

export const MY_PROFILE_QUERY_KEY = ['users', 'me'] as const;

export function useMyProfile() {
  return useQuery<MyProfileResponse, Error>({
    queryKey: MY_PROFILE_QUERY_KEY,
    queryFn: getMyProfile,
  });
}

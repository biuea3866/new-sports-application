/**
 * useUsers.ts — 사용자 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  getMe,
  registerUser,
  type UserDto,
  type RegisterUserRequest,
} from '../users';
import { usersKeys } from '../queryKeys';

export function useMeQuery(
  options?: Omit<UseQueryOptions<UserDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: usersKeys.me(),
    queryFn: getMe,
    ...options,
  });
}

export function useRegisterUserMutation(
  options?: UseMutationOptions<UserDto, Error, RegisterUserRequest>
) {
  return useMutation({
    mutationFn: registerUser,
    ...options,
  });
}

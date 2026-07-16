/**
 * query-client.ts — TanStack QueryClient + MMKV 영속화 설정
 *
 * - staleTime: 30분. 30분 이내 캐시는 네트워크 없이 반환.
 * - gcTime: 30분. 캐시 만료 후 GC 처리.
 * - retry: shouldRetryQuery — 4xx(401/403/404 등 클라이언트 오류)는 재시도해도
 *   결과가 바뀌지 않으므로 즉시 실패 처리하고, 그 외(네트워크·5xx)는 최대 2회 재시도.
 *   (버그3: 커뮤니티 비ACTIVE 멤버의 403을 무조건 2회 재시도해 화면이 깜빡이던 문제)
 */
import axios from 'axios';
import { QueryClient } from '@tanstack/react-query';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import { mmkvAsyncStorage } from './mmkv-storage';

const THIRTY_MINUTES_MS = 30 * 60 * 1000;
const MAX_RETRY_COUNT = 2;

/**
 * TanStack Query 공용 retry 판정 함수.
 * 4xx 응답(클라이언트 오류)은 재시도하지 않는다 — 권한 없음(401/403)·존재하지
 * 않음(404) 등은 재시도로 해결되지 않고, 재시도 자체가 화면 깜빡임의 원인이 된다.
 */
export function shouldRetryQuery(failureCount: number, error: unknown): boolean {
  if (axios.isAxiosError(error)) {
    const status = error.response?.status;
    if (status !== undefined && status >= 400 && status < 500) {
      return false;
    }
  }
  return failureCount < MAX_RETRY_COUNT;
}

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: THIRTY_MINUTES_MS,
      gcTime: THIRTY_MINUTES_MS,
      retry: shouldRetryQuery,
    },
  },
});

export const mmkvPersister = createAsyncStoragePersister({
  storage: mmkvAsyncStorage,
  key: 'tanstack-query-cache',
  throttleTime: 1000,
});

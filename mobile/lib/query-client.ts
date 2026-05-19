/**
 * query-client.ts — TanStack QueryClient + MMKV 영속화 설정
 *
 * - staleTime: 30분. 30분 이내 캐시는 네트워크 없이 반환.
 * - gcTime: 30분. 캐시 만료 후 GC 처리.
 * - retry: 2회 재시도.
 */
import { QueryClient } from '@tanstack/react-query';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import { mmkvAsyncStorage } from './mmkv-storage';

const THIRTY_MINUTES_MS = 30 * 60 * 1000;

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: THIRTY_MINUTES_MS,
      gcTime: THIRTY_MINUTES_MS,
      retry: 2,
    },
  },
});

export const mmkvPersister = createAsyncStoragePersister({
  storage: mmkvAsyncStorage,
  key: 'tanstack-query-cache',
  throttleTime: 1000,
});

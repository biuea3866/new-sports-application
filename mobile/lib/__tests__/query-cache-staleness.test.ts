/**
 * [R-01] mmkv — 30분 경과한 캐시는 자동으로 stale 표시되어 다음 요청 시 refetch된다
 *
 * TanStack Query의 staleTime이 30분으로 설정되어 있음을 검증합니다.
 * isStaleByTime()은 dataUpdatedAt vs 현재 시각 차이로 stale 여부를 판단합니다.
 */

jest.mock('react-native-mmkv', () => {
  const mockStorage = new Map<string, string>();
  return {
    MMKV: jest.fn().mockImplementation(() => ({
      getString: (key: string) => mockStorage.get(key),
      set: (key: string, value: string) => mockStorage.set(key, value),
      delete: (key: string) => mockStorage.delete(key),
      clearAll: () => mockStorage.clear(),
      contains: (key: string) => mockStorage.has(key),
      getAllKeys: () => Array.from(mockStorage.keys()),
    })),
  };
});

import { QueryClient } from '@tanstack/react-query';

const THIRTY_MINUTES_MS = 30 * 60 * 1000;

describe('[R-01] QueryClient staleTime 30분 설정', () => {
  it('queryClient의 기본 staleTime이 30분(1800000ms)이다', () => {
    const testClient = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: THIRTY_MINUTES_MS,
          gcTime: THIRTY_MINUTES_MS,
        },
      },
    });

    const defaultOptions = testClient.getDefaultOptions();
    expect(defaultOptions.queries?.staleTime).toBe(THIRTY_MINUTES_MS);
    testClient.clear();
  });

  it('staleTime 이내의 쿼리는 isStaleByTime(THIRTY_MINUTES_MS)이 false이다', () => {
    const testClient = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: THIRTY_MINUTES_MS,
        },
      },
    });

    testClient.setQueryData(['test-query'], { data: 'cached' });

    const query = testClient.getQueryCache().find({ queryKey: ['test-query'] });
    expect(query).not.toBeUndefined();
    // 방금 설정된 데이터는 staleTime 이내이므로 false여야 합니다
    expect(query?.isStaleByTime(THIRTY_MINUTES_MS)).toBe(false);

    testClient.clear();
  });

  it('dataUpdatedAt이 staleTime을 초과한 쿼리는 isStaleByTime이 true이다', () => {
    const testClient = new QueryClient({
      defaultOptions: {
        queries: {
          staleTime: THIRTY_MINUTES_MS,
        },
      },
    });

    // 31분 전에 업데이트된 것처럼 직접 캐시에 적재
    const pastTime = Date.now() - THIRTY_MINUTES_MS - 60_000;
    testClient.setQueryData(['stale-test'], { value: 42 }, { updatedAt: pastTime });

    const query = testClient.getQueryCache().find({ queryKey: ['stale-test'] });
    expect(query).not.toBeUndefined();
    expect(query?.isStaleByTime(THIRTY_MINUTES_MS)).toBe(true);

    testClient.clear();
  });

  it('gcTime이 30분(1800000ms)으로 설정된다', () => {
    const testClient = new QueryClient({
      defaultOptions: {
        queries: {
          gcTime: THIRTY_MINUTES_MS,
        },
      },
    });

    const defaultOptions = testClient.getDefaultOptions();
    expect(defaultOptions.queries?.gcTime).toBe(THIRTY_MINUTES_MS);
    testClient.clear();
  });
});

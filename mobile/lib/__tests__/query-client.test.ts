/**
 * [U-03] MMKVPersister — TanStack Query cache가 mmkv에 직렬화되어 저장된다
 */

// react-native-mmkv를 mock합니다. 네이티브 모듈 없이 테스트합니다.
// jest.mock factory 안에서 외부 변수 참조 불가이므로 factory 내부에서 Map을 생성합니다.
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

import axios from 'axios';
import { mmkvAsyncStorage } from '../mmkv-storage';
import { queryClient, shouldRetryQuery } from '../query-client';

describe('[U-03] MMKVPersister AsyncStorage 어댑터', () => {
  beforeEach(async () => {
    await mmkvAsyncStorage.removeItem('test-key');
    await mmkvAsyncStorage.removeItem('tanstack-query-cache');
  });

  it('setItem → getItem 라운드트립이 정확히 동작한다', async () => {
    await mmkvAsyncStorage.setItem('test-key', '{"hello":"world"}');
    const result = await mmkvAsyncStorage.getItem('test-key');
    expect(result).toBe('{"hello":"world"}');
  });

  it('존재하지 않는 키는 null을 반환한다', async () => {
    const result = await mmkvAsyncStorage.getItem('non-existent-key');
    expect(result).toBeNull();
  });

  it('removeItem 후 getItem은 null을 반환한다', async () => {
    await mmkvAsyncStorage.setItem('test-key', 'value');
    await mmkvAsyncStorage.removeItem('test-key');
    const result = await mmkvAsyncStorage.getItem('test-key');
    expect(result).toBeNull();
  });

  it('JSON 직렬화된 QueryClient 캐시를 저장하고 복원한다', async () => {
    const fakeCache = JSON.stringify({
      clientState: { queries: [{ queryKey: ['user'], state: { data: { id: 1 } } }] },
      timestamp: Date.now(),
    });
    await mmkvAsyncStorage.setItem('tanstack-query-cache', fakeCache);
    const restored = await mmkvAsyncStorage.getItem('tanstack-query-cache');
    expect(restored).not.toBeNull();
    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
    const parsed = JSON.parse(restored!);
    expect(parsed.clientState.queries[0].queryKey).toEqual(['user']);
  });
});

/**
 * [버그3] 4xx 응답(예: 커뮤니티 비ACTIVE 멤버의 403)에 대해 TanStack Query가
 * 재시도하지 않아야 한다 — 재시도는 "권한 없음/찾을 수 없음"을 바꾸지 못하고
 * 화면이 깜빡이며 지연 노출되는 원인이었다.
 */
describe('shouldRetryQuery — 4xx는 재시도하지 않는다', () => {
  function axiosErrorWithStatus(status: number): unknown {
    return Object.assign(new Error(`request failed with status ${status}`), {
      isAxiosError: true,
      response: { status },
    });
  }

  it('401은 재시도하지 않는다', () => {
    expect(shouldRetryQuery(0, axiosErrorWithStatus(401))).toBe(false);
  });

  it('403은 재시도하지 않는다', () => {
    expect(shouldRetryQuery(0, axiosErrorWithStatus(403))).toBe(false);
  });

  it('404는 첫 실패에서도 재시도하지 않는다', () => {
    expect(shouldRetryQuery(0, axiosErrorWithStatus(404))).toBe(false);
  });

  it('5xx는 실패 횟수가 2 미만이면 재시도한다', () => {
    expect(shouldRetryQuery(0, axiosErrorWithStatus(500))).toBe(true);
    expect(shouldRetryQuery(1, axiosErrorWithStatus(500))).toBe(true);
  });

  it('5xx도 실패 횟수가 2 이상이면 재시도를 멈춘다', () => {
    expect(shouldRetryQuery(2, axiosErrorWithStatus(500))).toBe(false);
  });

  it('axios 에러가 아닌 일반 에러는 실패 횟수 기준(2회 미만)으로 재시도한다', () => {
    expect(shouldRetryQuery(0, new Error('boom'))).toBe(true);
    expect(shouldRetryQuery(2, new Error('boom'))).toBe(false);
  });

  it('axios.isAxiosError로 판별 가능한 실제 AxiosError 형태도 동일하게 동작한다', () => {
    expect(axios.isAxiosError(axiosErrorWithStatus(403))).toBe(true);
  });

  it('실제 queryClient의 기본 retry 옵션으로 연결돼 있다', () => {
    expect(queryClient.getDefaultOptions().queries?.retry).toBe(shouldRetryQuery);
  });
});

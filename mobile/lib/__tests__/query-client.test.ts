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

import { mmkvAsyncStorage } from '../mmkv-storage';

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

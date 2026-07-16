/**
 * @jest-environment jsdom
 *
 * mmkv-storage.web 계약 테스트
 *
 * 웹 번들에서 Metro가 선택하는 mmkv-storage.web.ts는 네이티브 구현
 * (lib/mmkv-storage.ts)과 동일한 export 표면을 제공해야 합니다.
 * themeStore가 cacheStorage(getString/set/delete)를 직접 사용하므로,
 * 이 export가 빠지면 웹에서 테마 초기화 시점에 런타임 오류가 납니다.
 */
import { cacheStorage, mmkvAsyncStorage } from '../mmkv-storage.web';

describe('mmkv-storage.web', () => {
  beforeEach(() => {
    window.localStorage.clear();
  });

  describe('cacheStorage', () => {
    it('저장한 값을 getString으로 다시 읽는다', () => {
      cacheStorage.set('theme-preference', 'dark');

      expect(cacheStorage.getString('theme-preference')).toBe('dark');
    });

    it('저장한 적 없는 키는 undefined를 반환한다', () => {
      expect(cacheStorage.getString('없는-키')).toBeUndefined();
    });

    it('delete한 키는 다시 읽히지 않는다', () => {
      cacheStorage.set('theme-preference', 'dark');

      cacheStorage.delete('theme-preference');

      expect(cacheStorage.getString('theme-preference')).toBeUndefined();
    });

    it('localStorage에 실제로 영속된다', () => {
      cacheStorage.set('theme-preference', 'light');

      expect(window.localStorage.getItem('theme-preference')).toBe('light');
    });
  });

  describe('mmkvAsyncStorage', () => {
    it('setItem으로 저장한 값을 getItem으로 읽는다', async () => {
      await mmkvAsyncStorage.setItem('query-cache', '{"a":1}');

      await expect(mmkvAsyncStorage.getItem('query-cache')).resolves.toBe('{"a":1}');
    });

    it('removeItem 후에는 null을 반환한다', async () => {
      await mmkvAsyncStorage.setItem('query-cache', '{"a":1}');

      await mmkvAsyncStorage.removeItem('query-cache');

      await expect(mmkvAsyncStorage.getItem('query-cache')).resolves.toBeNull();
    });
  });
});

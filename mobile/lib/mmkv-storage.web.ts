/**
 * mmkv-storage.web.ts — 웹 전용 캐시 스토리지 fallback
 *
 * react-native-mmkv는 네이티브 전용이라 웹에 구현이 없다.
 * 웹 데모 실행을 위해 localStorage 기반 AsyncStorage 어댑터로 대체한다.
 * Metro가 웹 번들에서 mmkv-storage.ts 대신 이 파일을 선택한다.
 */
import type { AsyncStorage } from '@tanstack/query-persist-client-core';

function getStore(): Storage | null {
  return typeof window !== 'undefined' ? window.localStorage : null;
}

/**
 * 네이티브 MMKV 인스턴스(lib/mmkv-storage.ts의 cacheStorage)와 동일한
 * 동기 API를 localStorage로 제공합니다. themeStore가 이 표면을 직접 사용합니다.
 */
export const cacheStorage = {
  getString: (key: string): string | undefined => getStore()?.getItem(key) ?? undefined,
  set: (key: string, value: string): void => {
    getStore()?.setItem(key, value);
  },
  delete: (key: string): void => {
    getStore()?.removeItem(key);
  },
};

export const mmkvAsyncStorage: AsyncStorage<string> = {
  getItem: (key: string) => Promise.resolve(getStore()?.getItem(key) ?? null),
  setItem: (key: string, value: string) => {
    getStore()?.setItem(key, value);
    return Promise.resolve();
  },
  removeItem: (key: string) => {
    getStore()?.removeItem(key);
    return Promise.resolve();
  },
};

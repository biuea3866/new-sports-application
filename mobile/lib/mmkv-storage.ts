/**
 * mmkv-storage.ts — 비밀 아닌 캐시 전용 MMKV 인스턴스
 *
 * - 토큰 저장 금지. 토큰은 expo-secure-store만 사용할 것.
 * - TanStack Query persist용 AsyncStorage 어댑터 제공.
 */
import { MMKV } from 'react-native-mmkv';
import type { AsyncStorage } from '@tanstack/query-persist-client-core';

/** 앱 캐시 전용 MMKV 인스턴스 (비밀 정보 저장 금지) */
export const cacheStorage = new MMKV({ id: 'app-cache' });

/**
 * TanStack Query `createAsyncStoragePersister`가 요구하는 AsyncStorage 인터페이스로
 * MMKV를 래핑합니다.
 */
export const mmkvAsyncStorage: AsyncStorage<string> = {
  getItem: (key: string) => {
    const value = cacheStorage.getString(key);
    return Promise.resolve(value ?? null);
  },
  setItem: (key: string, value: string) => {
    cacheStorage.set(key, value);
    return Promise.resolve();
  },
  removeItem: (key: string) => {
    cacheStorage.delete(key);
    return Promise.resolve();
  },
};

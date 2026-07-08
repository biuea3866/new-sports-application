/**
 * theme/themeStore.ts — 테마 모드(system/light/dark) 사용자 오버라이드(전역, Zustand)
 *
 * "system"이 기본값이며, 사용자가 명시적으로 라이트/다크를 선택한 경우에만
 * 그 값을 사용합니다. MMKV(`lib/mmkv-storage`의 cacheStorage)에 영속되어
 * 앱 재기동 후에도 유지됩니다.
 */
import { create } from 'zustand';
import { persist, createJSONStorage, type StateStorage } from 'zustand/middleware';
import { cacheStorage } from '../lib/mmkv-storage';

export type ThemeMode = 'system' | 'light' | 'dark';

export interface ThemeStoreState {
  mode: ThemeMode;
  setMode: (mode: ThemeMode) => void;
}

const mmkvStateStorage: StateStorage = {
  getItem: (key: string) => cacheStorage.getString(key) ?? null,
  setItem: (key: string, value: string) => {
    cacheStorage.set(key, value);
  },
  removeItem: (key: string) => {
    cacheStorage.delete(key);
  },
};

export const useThemeStore = create<ThemeStoreState>()(
  persist(
    (set) => ({
      mode: 'system',
      setMode: (mode: ThemeMode) => {
        set({ mode });
      },
    }),
    {
      name: 'theme-storage',
      storage: createJSONStorage(() => mmkvStateStorage),
    }
  )
);

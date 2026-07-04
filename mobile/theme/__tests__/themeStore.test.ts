/**
 * theme/themeStore.ts — 테마 모드(system/light/dark) 전역 상태 + MMKV 영속
 */
import { act } from '@testing-library/react-native';
import { useThemeStore } from '../themeStore';
import { cacheStorage } from '../../lib/mmkv-storage';

const STORAGE_KEY = 'theme-storage';

describe('useThemeStore', () => {
  beforeEach(() => {
    act(() => {
      useThemeStore.getState().setMode('system');
    });
  });

  it('초기 모드는 system이다', () => {
    expect(useThemeStore.getState().mode).toBe('system');
  });

  it('setMode로 light를 지정하면 상태에 반영된다', () => {
    act(() => {
      useThemeStore.getState().setMode('light');
    });

    expect(useThemeStore.getState().mode).toBe('light');
  });

  it('setMode로 dark를 지정하면 상태에 반영된다', () => {
    act(() => {
      useThemeStore.getState().setMode('dark');
    });

    expect(useThemeStore.getState().mode).toBe('dark');
  });

  it('setMode 변경이 MMKV 스토리지에 영속된다', () => {
    act(() => {
      useThemeStore.getState().setMode('dark');
    });

    const persisted = cacheStorage.getString(STORAGE_KEY);
    expect(persisted).toBeDefined();
    expect(JSON.parse(persisted as string).state.mode).toBe('dark');
  });

  it('MMKV에 저장된 값으로부터 재생성(rehydrate) 시 모드가 복원된다', () => {
    cacheStorage.set(STORAGE_KEY, JSON.stringify({ state: { mode: 'light' }, version: 0 }));

    act(() => {
      useThemeStore.persist.rehydrate();
    });

    expect(useThemeStore.getState().mode).toBe('light');
  });
});

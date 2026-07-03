/**
 * useThemeStore — 테마 스킴 사용자 오버라이드(전역, Zustand)
 */
import { act } from '@testing-library/react-native';
import { useThemeStore } from '../useThemeStore';

describe('useThemeStore', () => {
  beforeEach(() => {
    act(() => {
      useThemeStore.getState().setSchemeOverride(null);
    });
  });

  it('초기 상태는 오버라이드 없음(null)이다', () => {
    expect(useThemeStore.getState().schemeOverride).toBeNull();
  });

  it('setSchemeOverride로 다크를 지정하면 상태에 반영된다', () => {
    act(() => {
      useThemeStore.getState().setSchemeOverride('dark');
    });

    expect(useThemeStore.getState().schemeOverride).toBe('dark');
  });

  it('setSchemeOverride(null)로 시스템 기본값으로 되돌릴 수 있다', () => {
    act(() => {
      useThemeStore.getState().setSchemeOverride('light');
    });
    act(() => {
      useThemeStore.getState().setSchemeOverride(null);
    });

    expect(useThemeStore.getState().schemeOverride).toBeNull();
  });
});

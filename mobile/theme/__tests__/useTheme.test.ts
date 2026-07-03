/**
 * useTheme — 시스템 색상 스킴 기본값 + 사용자 오버라이드로 현재 토큰을 반환합니다.
 */
import { renderHook, act } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { useTheme } from '../useTheme';
import { useThemeStore } from '../../stores/useThemeStore';
import { lightTokens, darkTokens } from '../tokens';

describe('useTheme', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    act(() => {
      useThemeStore.getState().setSchemeOverride(null);
    });
  });

  it('시스템 스킴이 라이트일 때 라이트 토큰 객체를 반환한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    const { result } = renderHook(() => useTheme());

    expect(result.current.scheme).toBe('light');
    expect(result.current.tokens).toEqual(lightTokens);
  });

  it('useThemeStore로 다크 오버라이드 시 시스템 스킴과 무관하게 다크 토큰을 반환한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    const { result } = renderHook(() => useTheme());

    act(() => {
      useThemeStore.getState().setSchemeOverride('dark');
    });

    expect(result.current.scheme).toBe('dark');
    expect(result.current.tokens).toEqual(darkTokens);
  });

  it('오버라이드가 없고 시스템 스킴이 다크이면 다크 토큰을 반환한다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    const { result } = renderHook(() => useTheme());

    expect(result.current.scheme).toBe('dark');
    expect(result.current.tokens).toEqual(darkTokens);
  });
});

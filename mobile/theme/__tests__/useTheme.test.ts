/**
 * useTheme — 시스템 색상 스킴 기본값 + themeStore(mode) 오버라이드로 현재 토큰을 반환합니다.
 */
import { renderHook, act } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { useTheme } from '../useTheme';
import { useThemeStore } from '../themeStore';
import { lightTokens, darkTokens } from '../tokens';

describe('useTheme', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    act(() => {
      useThemeStore.getState().setMode('system');
    });
  });

  it('시스템이 다크 모드이고 mode가 system이면 다크 토큰 객체를 반환한다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    const { result } = renderHook(() => useTheme());

    expect(result.current.scheme).toBe('dark');
    expect(result.current.tokens).toEqual(darkTokens);
  });

  it('mode를 light로 오버라이드하면 시스템이 다크여도 라이트 토큰을 반환한다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    act(() => {
      useThemeStore.getState().setMode('light');
    });

    const { result } = renderHook(() => useTheme());

    expect(result.current.scheme).toBe('light');
    expect(result.current.tokens).toEqual(lightTokens);
  });

  it('mode가 system이면 useColorScheme 값을 따른다(라이트)', () => {
    mockUseColorScheme.mockReturnValue('light');

    const { result } = renderHook(() => useTheme());

    expect(result.current.scheme).toBe('light');
    expect(result.current.tokens).toEqual(lightTokens);
  });

  it('mode를 dark로 오버라이드하면 시스템이 라이트여도 다크 토큰을 반환한다', () => {
    mockUseColorScheme.mockReturnValue('light');
    act(() => {
      useThemeStore.getState().setMode('dark');
    });

    const { result } = renderHook(() => useTheme());

    expect(result.current.scheme).toBe('dark');
    expect(result.current.tokens).toEqual(darkTokens);
  });
});

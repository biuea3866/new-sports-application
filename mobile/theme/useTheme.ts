/**
 * useTheme — 현재 스킴의 테마 토큰 객체를 반환합니다.
 *
 * 우선순위: useThemeStore의 사용자 오버라이드 > useColorScheme() 시스템 값 > 라이트(fallback).
 */
import { useColorScheme } from 'react-native';
import { useThemeStore } from '../stores/useThemeStore';
import { themeTokens, type ColorScheme, type ThemeTokens } from './tokens';

export interface UseThemeResult {
  scheme: ColorScheme;
  tokens: ThemeTokens;
}

function resolveScheme(
  schemeOverride: ColorScheme | null,
  systemScheme: 'light' | 'dark' | null | undefined
): ColorScheme {
  if (schemeOverride !== null) {
    return schemeOverride;
  }
  return systemScheme === 'dark' ? 'dark' : 'light';
}

export function useTheme(): UseThemeResult {
  const systemScheme = useColorScheme();
  const schemeOverride = useThemeStore((state) => state.schemeOverride);
  const scheme = resolveScheme(schemeOverride, systemScheme);

  return {
    scheme,
    tokens: themeTokens[scheme],
  };
}

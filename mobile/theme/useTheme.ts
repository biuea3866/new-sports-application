/**
 * useTheme — 현재 모드의 테마 토큰 객체를 반환합니다.
 *
 * 우선순위: themeStore의 mode가 'light'|'dark'로 오버라이드되어 있으면 그 값을,
 * mode가 'system'이면 useColorScheme() 시스템 값을(다크가 아니면 라이트로) 사용합니다.
 */
import { useColorScheme } from 'react-native';
import { useThemeStore, type ThemeMode } from './themeStore';
import { themeTokens, type ColorScheme, type ThemeTokens } from './tokens';

export interface UseThemeResult {
  scheme: ColorScheme;
  tokens: ThemeTokens;
}

function resolveScheme(
  mode: ThemeMode,
  systemScheme: 'light' | 'dark' | null | undefined
): ColorScheme {
  if (mode === 'light' || mode === 'dark') {
    return mode;
  }
  return systemScheme === 'dark' ? 'dark' : 'light';
}

export function useTheme(): UseThemeResult {
  const systemScheme = useColorScheme();
  const mode = useThemeStore((state) => state.mode);
  const scheme = resolveScheme(mode, systemScheme);

  return {
    scheme,
    tokens: themeTokens[scheme],
  };
}

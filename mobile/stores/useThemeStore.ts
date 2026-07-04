/**
 * useThemeStore — 테마 스킴 사용자 오버라이드 (전역 상태)
 *
 * 시스템 스킴(useColorScheme)이 기본값이며, 사용자가 명시적으로 오버라이드한
 * 경우에만 이 스토어의 값을 사용합니다. "정말 전역"인 테마 스킴만 다루며
 * 그 외 화면/서버 상태는 이 스토어에 두지 않습니다.
 */
import { create } from 'zustand';
import type { ColorScheme } from '../theme/tokens';

interface ThemeState {
  /** null이면 시스템 스킴을 따릅니다 */
  schemeOverride: ColorScheme | null;
  setSchemeOverride: (scheme: ColorScheme | null) => void;
}

export const useThemeStore = create<ThemeState>((set) => ({
  schemeOverride: null,
  setSchemeOverride: (scheme: ColorScheme | null) => {
    set({ schemeOverride: scheme });
  },
}));

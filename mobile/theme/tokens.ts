/**
 * theme/tokens.ts — 시맨틱 테마 토큰 정의 (SSOT)
 *
 * design-fe-app.md "테마 토큰 정의 표"의 라이트/다크 값을 그대로 반영합니다.
 * 색 하드코딩(hex)은 이 파일에만 존재해야 하며, 화면·컴포넌트는 반드시
 * useTheme()이 반환하는 토큰 객체를 경유해 색을 참조합니다.
 * (아래 hex 리터럴은 토큰 정의 자체이므로 private-fe-convention no-hardcoded-color의
 * 예외 대상입니다 — private-allow 주석으로 명시)
 */

export type ColorScheme = 'light' | 'dark';

export interface ThemeTokens {
  /** 화면 최하단 배경 */
  background: string;
  /** 카드/그룹 배경 */
  surface: string;
  /** 카드 위 카드 */
  surfaceElevated: string;
  /** 제목·본문 */
  textPrimary: string;
  /** 보조 텍스트 */
  textSecondary: string;
  /** 힌트·placeholder */
  textMuted: string;
  /** 구분선 */
  border: string;
  /** 단일 CTA·가격 강조 (화면당 1곳) */
  accent: string;
  /** accent 위 텍스트 */
  accentText: string;
  /** 오류·소진 */
  danger: string;
  /** 혼잡(429) */
  warning: string;
  /** 구매 성공 */
  success: string;
  /** 비활성 버튼/배경 */
  disabled: string;
}

export const lightTokens: ThemeTokens = {
  background: '#FFFFFF', // private-allow:no-hardcoded-color
  surface: '#F2F2F7', // private-allow:no-hardcoded-color
  surfaceElevated: '#FFFFFF', // private-allow:no-hardcoded-color
  textPrimary: '#1C1C1E', // private-allow:no-hardcoded-color
  textSecondary: '#6C6C70', // private-allow:no-hardcoded-color
  textMuted: '#8E8E93', // private-allow:no-hardcoded-color
  border: '#E5E5EA', // private-allow:no-hardcoded-color
  accent: '#007AFF', // private-allow:no-hardcoded-color
  accentText: '#FFFFFF', // private-allow:no-hardcoded-color
  danger: '#FF3B30', // private-allow:no-hardcoded-color
  warning: '#FF9500', // private-allow:no-hardcoded-color
  success: '#34C759', // private-allow:no-hardcoded-color
  disabled: '#C7C7CC', // private-allow:no-hardcoded-color
};

export const darkTokens: ThemeTokens = {
  background: '#000000', // private-allow:no-hardcoded-color
  surface: '#1C1C1E', // private-allow:no-hardcoded-color
  surfaceElevated: '#2C2C2E', // private-allow:no-hardcoded-color
  textPrimary: '#FFFFFF', // private-allow:no-hardcoded-color
  textSecondary: '#AEAEB2', // private-allow:no-hardcoded-color
  textMuted: '#8E8E93', // private-allow:no-hardcoded-color
  border: '#38383A', // private-allow:no-hardcoded-color
  accent: '#0A84FF', // private-allow:no-hardcoded-color
  accentText: '#FFFFFF', // private-allow:no-hardcoded-color
  danger: '#FF453A', // private-allow:no-hardcoded-color
  warning: '#FF9F0A', // private-allow:no-hardcoded-color
  success: '#30D158', // private-allow:no-hardcoded-color
  disabled: '#48484A', // private-allow:no-hardcoded-color
};

export const themeTokens: Record<ColorScheme, ThemeTokens> = {
  light: lightTokens,
  dark: darkTokens,
};

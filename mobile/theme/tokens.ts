/**
 * theme/tokens.ts — 시맨틱 테마 토큰 정의 (SSOT)
 *
 * design-fe-app.md "테마 토큰 정의 표"의 라이트/다크 값을 그대로 반영합니다.
 * 색 하드코딩(hex)은 이 파일에만 존재해야 하며, 화면·컴포넌트는 반드시
 * useTheme()이 반환하는 토큰 객체를 경유해 색을 참조합니다.
 * (아래 hex 리터럴은 토큰 정의 자체이므로 private-fe-convention no-hardcoded-color의
 * 예외 대상입니다 — private-allow 주석으로 명시)
 *
 * textMuted/warning/disabled는 기존(상품/한정판) 화면이 참조하는 레거시 토큰으로,
 * 설계 문서 표에는 없으나 하위 호환을 위해 유지합니다.
 */

export type ColorScheme = 'light' | 'dark';

export interface ThemeTokens {
  /** 화면 배경 */
  background: string;
  /** 카드·리스트 아이템 배경 */
  surface: string;
  /** 입력창·시트 */
  surfaceElevated: string;
  /** 본문·제목 */
  textPrimary: string;
  /** 미리보기·메타 */
  textSecondary: string;
  /** 시각·placeholder */
  textTertiary: string;
  /** 힌트 텍스트 (레거시 — 상품/한정판 화면 호환용) */
  textMuted: string;
  /** 구분선·테두리 */
  border: string;
  /** 주요 CTA·활성 탭 (화면당 1곳) */
  accent: string;
  /** accent 위 텍스트 */
  accentText: string;
  /** 내 말풍선 */
  bubbleMine: string;
  /** 내 말풍선 텍스트 */
  bubbleMineText: string;
  /** 상대 말풍선 */
  bubbleOther: string;
  /** 상대 말풍선 텍스트 */
  bubbleOtherText: string;
  /** 안읽은 배지 배경 */
  badge: string;
  /** 배지 텍스트 */
  badgeText: string;
  /** 읽음·온라인 */
  success: string;
  /** 오류·강퇴·거절 */
  danger: string;
  /** 혼잡(429) 경고 (레거시 — 상품/한정판 화면 호환용) */
  warning: string;
  /** 비활성 버튼/배경 (레거시 — 상품/한정판 화면 호환용) */
  disabled: string;
  /** 시트 딤 */
  overlay: string;
  /** 타이핑 인디케이터 */
  typing: string;
}

export const lightTokens: ThemeTokens = {
  background: '#FFFFFF', // private-allow:no-hardcoded-color
  surface: '#F9FAFB', // private-allow:no-hardcoded-color
  surfaceElevated: '#FFFFFF', // private-allow:no-hardcoded-color
  textPrimary: '#191F28', // private-allow:no-hardcoded-color
  textSecondary: '#4E5968', // private-allow:no-hardcoded-color
  textTertiary: '#8B95A1', // private-allow:no-hardcoded-color
  textMuted: '#8E8E93', // private-allow:no-hardcoded-color
  border: '#E5E8EB', // private-allow:no-hardcoded-color
  accent: '#3182F6', // private-allow:no-hardcoded-color
  accentText: '#FFFFFF', // private-allow:no-hardcoded-color
  bubbleMine: '#3182F6', // private-allow:no-hardcoded-color
  bubbleMineText: '#FFFFFF', // private-allow:no-hardcoded-color
  bubbleOther: '#F2F4F6', // private-allow:no-hardcoded-color
  bubbleOtherText: '#191F28', // private-allow:no-hardcoded-color
  badge: '#F04452', // private-allow:no-hardcoded-color
  badgeText: '#FFFFFF', // private-allow:no-hardcoded-color
  success: '#12B886', // private-allow:no-hardcoded-color
  danger: '#F04452', // private-allow:no-hardcoded-color
  warning: '#FF9500', // private-allow:no-hardcoded-color
  disabled: '#C7C7CC', // private-allow:no-hardcoded-color
  overlay: 'rgba(0,0,0,0.4)', // private-allow:no-hardcoded-color
  typing: '#8B95A1', // private-allow:no-hardcoded-color
};

export const darkTokens: ThemeTokens = {
  background: '#17171C', // private-allow:no-hardcoded-color
  surface: '#202027', // private-allow:no-hardcoded-color
  surfaceElevated: '#26262E', // private-allow:no-hardcoded-color
  textPrimary: '#F2F4F6', // private-allow:no-hardcoded-color
  textSecondary: '#B0B8C1', // private-allow:no-hardcoded-color
  textTertiary: '#6B7684', // private-allow:no-hardcoded-color
  textMuted: '#8E8E93', // private-allow:no-hardcoded-color
  border: '#2E2E36', // private-allow:no-hardcoded-color
  accent: '#4E93FB', // private-allow:no-hardcoded-color
  accentText: '#FFFFFF', // private-allow:no-hardcoded-color
  bubbleMine: '#3B5BDB', // private-allow:no-hardcoded-color
  bubbleMineText: '#F2F4F6', // private-allow:no-hardcoded-color
  bubbleOther: '#2E2E36', // private-allow:no-hardcoded-color
  bubbleOtherText: '#F2F4F6', // private-allow:no-hardcoded-color
  badge: '#F76A78', // private-allow:no-hardcoded-color
  badgeText: '#FFFFFF', // private-allow:no-hardcoded-color
  success: '#2AC29B', // private-allow:no-hardcoded-color
  danger: '#F76A78', // private-allow:no-hardcoded-color
  warning: '#FF9F0A', // private-allow:no-hardcoded-color
  disabled: '#48484A', // private-allow:no-hardcoded-color
  overlay: 'rgba(0,0,0,0.6)', // private-allow:no-hardcoded-color
  typing: '#6B7684', // private-allow:no-hardcoded-color
};

export const themeTokens: Record<ColorScheme, ThemeTokens> = {
  light: lightTokens,
  dark: darkTokens,
};

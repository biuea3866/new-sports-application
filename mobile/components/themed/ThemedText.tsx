/**
 * ThemedText — 테마 토큰의 텍스트 색을 사용하는 Text 프리미티브.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { Text, type TextProps } from 'react-native';
import { useTheme } from '../../theme/useTheme';
import type { ThemeTokens } from '../../theme/tokens';

export type ThemedTextVariant =
  | 'primary'
  | 'secondary'
  | 'muted'
  | 'accent'
  | 'onAccent'
  | 'danger'
  | 'warning'
  | 'success';

const VARIANT_TO_TOKEN: Record<ThemedTextVariant, keyof ThemeTokens> = {
  primary: 'textPrimary',
  secondary: 'textSecondary',
  muted: 'textMuted',
  accent: 'accent',
  onAccent: 'accentText',
  danger: 'danger',
  warning: 'warning',
  success: 'success',
};

export interface ThemedTextProps extends TextProps {
  variant?: ThemedTextVariant;
}

export function ThemedText({ variant = 'primary', style, ...rest }: ThemedTextProps) {
  const { tokens } = useTheme();
  const color = tokens[VARIANT_TO_TOKEN[variant]];

  return <Text style={[{ color }, style]} {...rest} />;
}

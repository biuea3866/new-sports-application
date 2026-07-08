/**
 * ThemedView — 테마 토큰의 배경색을 사용하는 View 프리미티브.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { View, type ViewProps } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export type ThemedViewBackground = 'background' | 'surface' | 'surfaceElevated';

export interface ThemedViewProps extends ViewProps {
  background?: ThemedViewBackground;
}

export function ThemedView({ background = 'background', style, ...rest }: ThemedViewProps) {
  const { tokens } = useTheme();

  return <View style={[{ backgroundColor: tokens[background] }, style]} {...rest} />;
}

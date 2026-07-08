/**
 * Avatar — 이름의 이니셜을 원형 플레이스홀더로 렌더합니다(이미지 없음, 순수 프레젠테이션).
 * 이름이 없으면 "?"를 표시합니다. 색은 항상 useTheme() 토큰을 경유합니다.
 */
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export interface AvatarProps {
  name: string;
  size?: number;
}

const DEFAULT_SIZE = 40;
const PLACEHOLDER = '?';

function resolveInitial(name: string): string {
  const trimmed = name.trim();
  if (trimmed.length === 0) {
    return PLACEHOLDER;
  }
  return trimmed.charAt(0).toUpperCase();
}

export function Avatar({ name, size = DEFAULT_SIZE }: AvatarProps) {
  const { tokens } = useTheme();
  const initial = resolveInitial(name);
  const accessibilityLabel = name.trim().length > 0 ? `${name} 아바타` : '아바타';

  return (
    <View
      style={[
        styles.circle,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: tokens.surface,
          borderColor: tokens.border,
        },
      ]}
      accessible
      accessibilityRole="image"
      accessibilityLabel={accessibilityLabel}
    >
      <Text style={[styles.initial, { color: tokens.textPrimary, fontSize: size * 0.4 }]}>
        {initial}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  circle: {
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  initial: {
    fontWeight: '700',
  },
});

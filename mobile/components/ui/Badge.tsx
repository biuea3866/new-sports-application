/**
 * Badge — 안읽은 수 원형 배지. count>0이면 숫자를(99 초과 시 "99+"), count<=0이면 아무것도 렌더하지 않습니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { View, Text, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export interface BadgeProps {
  count: number;
}

const MAX_DISPLAY_COUNT = 99;

export function Badge({ count }: BadgeProps) {
  const { tokens } = useTheme();

  if (count <= 0) {
    return null;
  }

  const displayCount = count > MAX_DISPLAY_COUNT ? `${MAX_DISPLAY_COUNT}+` : String(count);

  return (
    <View
      style={[styles.badge, { backgroundColor: tokens.badge }]}
      accessible
      accessibilityRole="text"
      accessibilityLabel={`안읽은 메시지 ${displayCount}개`}
    >
      <Text style={[styles.text, { color: tokens.badgeText }]}>{displayCount}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    minWidth: 20,
    height: 20,
    borderRadius: 10,
    paddingHorizontal: 5,
    alignItems: 'center',
    justifyContent: 'center',
  },
  text: {
    fontSize: 12,
    fontWeight: '700',
  },
});

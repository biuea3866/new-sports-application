/**
 * RemainingStockBar — 남은 수량(remaining/limited)을 텍스트와 진행 바로 표시하는 프레젠테이션 컴포넌트.
 * OPEN 상태 hero에서 사용됩니다. 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { StyleSheet, View } from 'react-native';
import { useTheme } from '../../theme/useTheme';
import { ThemedText } from '../themed/ThemedText';

export interface RemainingStockBarProps {
  /** 현재 남은 수량 */
  remaining: number;
  /** 회차 전체 한정 수량 */
  limited: number;
}

function calculateRemainingRatio(remaining: number, limited: number): number {
  if (limited <= 0) {
    return 0;
  }
  const ratio = remaining / limited;
  return Math.min(Math.max(ratio, 0), 1);
}

export function RemainingStockBar({ remaining, limited }: RemainingStockBarProps) {
  const { tokens } = useTheme();
  const ratio = calculateRemainingRatio(remaining, limited);

  return (
    <View accessible accessibilityLabel={`남은 수량 ${remaining}개 중 ${limited}개`}>
      <ThemedText variant="secondary" style={styles.label}>
        {`남은 수량 ${remaining}/${limited}`}
      </ThemedText>
      <View style={[styles.track, { backgroundColor: tokens.border }]}>
        <View style={[styles.fill, { backgroundColor: tokens.accent, width: `${ratio * 100}%` }]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  label: {
    fontSize: 14,
    marginBottom: 6,
  },
  track: {
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 4,
  },
});

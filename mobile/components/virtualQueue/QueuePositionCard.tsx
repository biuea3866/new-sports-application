/**
 * QueuePositionCard — 대기실 내 순번을 대형 숫자로 중앙 강조 표시하는 프레젠테이션 컴포넌트.
 * 로직 없음(props만 렌더). 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export interface QueuePositionCardProps {
  /** 내 순번 (1-based) */
  position: number;
}

function formatPosition(position: number): string {
  return position.toLocaleString('ko-KR');
}

export function QueuePositionCard({ position }: QueuePositionCardProps) {
  const { tokens } = useTheme();
  const formattedPosition = formatPosition(position);

  return (
    <View accessible accessibilityRole="text" accessibilityLabel={`내 순번 ${position}번`}>
      <Text style={[styles.position, { color: tokens.textPrimary }]}>{formattedPosition}</Text>
      <Text style={[styles.caption, { color: tokens.textTertiary }]}>내 순번</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  position: {
    fontSize: 48,
    fontWeight: '700',
    textAlign: 'center',
  },
  caption: {
    fontSize: 14,
    textAlign: 'center',
    marginTop: 4,
  },
});

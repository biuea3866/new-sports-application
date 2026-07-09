/**
 * QueueWaitInfo — 앞선 대기 인원·예상 대기 ETA 보조 텍스트를 표시하는 프레젠테이션 컴포넌트.
 * 값이 null이면 해당 줄을 렌더하지 않습니다. 로직 없음(props만 렌더).
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export interface QueueWaitInfoProps {
  /** 앞선 대기 인원. null이면 해당 줄 미표시 */
  aheadCount: number | null;
  /** 예상 대기 라벨(예: "약 4분"). null이면 해당 줄 미표시 */
  etaLabel: string | null;
}

function formatAheadCount(aheadCount: number): string {
  return `앞선 대기 ${aheadCount.toLocaleString('ko-KR')}명`;
}

export function QueueWaitInfo({ aheadCount, etaLabel }: QueueWaitInfoProps) {
  const { tokens } = useTheme();

  if (aheadCount === null && etaLabel === null) {
    return null;
  }

  return (
    <View accessible accessibilityRole="text" accessibilityLabel="대기 정보">
      {aheadCount !== null && (
        <Text style={[styles.text, { color: tokens.textSecondary }]}>
          {formatAheadCount(aheadCount)}
        </Text>
      )}
      {etaLabel !== null && (
        <Text
          style={[styles.text, { color: tokens.textSecondary }]}
        >{`예상 대기 ${etaLabel}`}</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  text: {
    fontSize: 14,
    textAlign: 'center',
    marginTop: 4,
  },
});

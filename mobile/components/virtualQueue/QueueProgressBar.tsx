/**
 * QueueProgressBar — 대기 진행률을 track+fill 진행바로 표시하는 프레젠테이션 컴포넌트.
 * RemainingStockBar(components/limitedDrop) 패턴 재사용. ratio는 [0,1]로 clamp됩니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음, accent는 화면당 1곳).
 */
import { StyleSheet, Text, View } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export interface QueueProgressBarProps {
  /** 진행 비율. [0,1] 범위 밖 값은 clamp됩니다 */
  ratio: number;
  /** 화면에 표시할 퍼센트 라벨(예: "62%") */
  percentLabel: string;
}

function clampRatio(ratio: number): number {
  return Math.min(Math.max(ratio, 0), 1);
}

export function QueueProgressBar({ ratio, percentLabel }: QueueProgressBarProps) {
  const { tokens } = useTheme();
  const clampedRatio = clampRatio(ratio);

  return (
    <View
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={`대기 진행률 ${percentLabel}`}
    >
      <View style={[styles.row]}>
        <View style={[styles.track, { backgroundColor: tokens.border }]}>
          <View
            testID="queue-progress-fill"
            style={[
              styles.fill,
              { backgroundColor: tokens.accent, width: `${clampedRatio * 100}%` },
            ]}
          />
        </View>
        <Text style={[styles.label, { color: tokens.textSecondary }]}>{percentLabel}</Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  track: {
    flex: 1,
    height: 8,
    borderRadius: 4,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: 4,
  },
  label: {
    fontSize: 14,
    marginLeft: 8,
  },
});

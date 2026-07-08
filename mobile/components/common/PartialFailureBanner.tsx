/**
 * PartialFailureBanner — 부분 실패(FR-11) 안내 배너. catalog·orders가 공유하는
 * 제네릭 프레젠테이션 컴포넌트입니다. labels가 빈 배열이면 아무것도 렌더하지 않습니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { View, StyleSheet } from 'react-native';
import { ThemedText } from '../themed/ThemedText';
import { useTheme } from '../../theme/useTheme';

export interface PartialFailureBannerProps {
  labels: string[];
}

const MESSAGE = '일부 결과를 불러오지 못했어요';

export function PartialFailureBanner({ labels }: PartialFailureBannerProps) {
  const { tokens } = useTheme();

  if (labels.length === 0) {
    return null;
  }

  const labelText = labels.join(', ');

  return (
    <View
      testID="partial-failure-banner"
      style={[
        styles.container,
        { backgroundColor: tokens.surfaceElevated, borderColor: tokens.border },
      ]}
    >
      <View style={[styles.accentBar, { backgroundColor: tokens.warning }]} />
      <View style={styles.content}>
        <ThemedText variant="warning" style={styles.icon}>
          ⚠
        </ThemedText>
        <View style={styles.textGroup}>
          <ThemedText
            variant="secondary"
            style={styles.message}
            accessibilityRole="alert"
            accessibilityLabel={`${MESSAGE} ${labelText}`}
          >
            {MESSAGE}
          </ThemedText>
          <ThemedText variant="secondary" style={styles.labels}>
            {labelText}
          </ThemedText>
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 8,
    marginHorizontal: 16,
    marginBottom: 12,
    overflow: 'hidden',
  },
  accentBar: {
    width: 4,
  },
  content: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingVertical: 10,
    paddingHorizontal: 12,
  },
  icon: {
    fontSize: 15,
    marginRight: 8,
    lineHeight: 20,
  },
  textGroup: {
    flex: 1,
  },
  message: {
    fontSize: 13,
    fontWeight: '600',
  },
  labels: {
    fontSize: 12,
    marginTop: 2,
  },
});

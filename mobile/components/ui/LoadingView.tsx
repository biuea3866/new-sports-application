/**
 * LoadingView — 로딩 상태를 스피너(spinner) 또는 스켈레톤(skeleton) 카드 목록으로 표시합니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { ActivityIndicator, View, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/useTheme';

export type LoadingViewVariant = 'spinner' | 'skeleton';

export interface LoadingViewProps {
  variant?: LoadingViewVariant;
  skeletonCount?: number;
  testID?: string;
}

const DEFAULT_SKELETON_COUNT = 3;
const LOADING_LABEL = '로딩 중';

export function LoadingView({
  variant = 'spinner',
  skeletonCount = DEFAULT_SKELETON_COUNT,
  testID,
}: LoadingViewProps) {
  const { tokens } = useTheme();

  if (variant === 'skeleton') {
    return (
      <View
        style={styles.skeletonContainer}
        accessible
        accessibilityRole="progressbar"
        accessibilityLabel={LOADING_LABEL}
      >
        {Array.from({ length: skeletonCount }).map((_, index) => (
          <View
            key={index}
            testID={testID}
            style={[styles.skeletonItem, { backgroundColor: tokens.surface }]}
          />
        ))}
      </View>
    );
  }

  return (
    <View
      style={styles.spinnerContainer}
      accessible
      accessibilityRole="progressbar"
      accessibilityLabel={LOADING_LABEL}
    >
      <ActivityIndicator size="large" color={tokens.accent} />
    </View>
  );
}

const styles = StyleSheet.create({
  spinnerContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 32,
  },
  skeletonContainer: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  skeletonItem: {
    height: 64,
    borderRadius: 12,
    marginBottom: 12,
  },
});

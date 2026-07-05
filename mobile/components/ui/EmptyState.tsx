/**
 * EmptyState — 전달된 안내 문구(및 선택적 보조 설명)를 렌더하는 빈 상태 프레젠테이션.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { View, StyleSheet } from 'react-native';
import { ThemedText } from '../themed/ThemedText';

export interface EmptyStateProps {
  message: string;
  description?: string;
}

export function EmptyState({ message, description }: EmptyStateProps) {
  return (
    <View style={styles.container} accessible accessibilityRole="text" accessibilityLabel={message}>
      <ThemedText variant="secondary" style={styles.message}>
        {message}
      </ThemedText>
      {description ? (
        <ThemedText variant="muted" style={styles.description}>
          {description}
        </ThemedText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 48,
    paddingHorizontal: 24,
  },
  message: {
    fontSize: 15,
    fontWeight: '600',
    textAlign: 'center',
  },
  description: {
    fontSize: 13,
    marginTop: 6,
    textAlign: 'center',
  },
});

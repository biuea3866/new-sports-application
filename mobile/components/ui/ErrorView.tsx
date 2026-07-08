/**
 * ErrorView — 오류 메시지를 표시하고 재시도 버튼 탭 시 콜백을 호출합니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { View, StyleSheet } from 'react-native';
import { ThemedText } from '../themed/ThemedText';
import { Button } from './Button';

export interface ErrorViewProps {
  message: string;
  onRetry: () => void;
}

export function ErrorView({ message, onRetry }: ErrorViewProps) {
  return (
    <View style={styles.container}>
      <ThemedText
        variant="danger"
        style={styles.message}
        accessibilityRole="alert"
        accessibilityLabel={message}
      >
        {message}
      </ThemedText>
      <View style={styles.action}>
        <Button label="다시 시도" onPress={onRetry} variant="surface" />
      </View>
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
    marginBottom: 16,
  },
  action: {
    minWidth: 140,
  },
});

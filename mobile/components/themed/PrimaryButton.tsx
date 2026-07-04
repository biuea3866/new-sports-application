/**
 * PrimaryButton — 화면당 단일 CTA를 위한 테마 프리미티브 버튼.
 * disabled 시 disabled 토큰 색 + 접근성 상태(accessibilityState.disabled)를 반영합니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { TouchableOpacity, StyleSheet, type GestureResponderEvent } from 'react-native';
import { useTheme } from '../../theme/useTheme';
import { ThemedText } from './ThemedText';

export interface PrimaryButtonProps {
  label: string;
  onPress: (event: GestureResponderEvent) => void;
  disabled?: boolean;
}

export function PrimaryButton({ label, onPress, disabled = false }: PrimaryButtonProps) {
  const { tokens } = useTheme();

  return (
    <TouchableOpacity
      style={[styles.button, { backgroundColor: disabled ? tokens.disabled : tokens.accent }]}
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
    >
      <ThemedText variant="onAccent" style={styles.label}>
        {label}
      </ThemedText>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 8,
    alignItems: 'center',
  },
  label: {
    fontSize: 16,
    fontWeight: '700',
  },
});

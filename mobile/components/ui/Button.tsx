/**
 * Button — accent(주요)/surface(보조) variant CTA 프리미티브. disabled·loading 상태를 지원합니다.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { ActivityIndicator, TouchableOpacity, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/useTheme';
import { ThemedText } from '../themed/ThemedText';

export type ButtonVariant = 'accent' | 'surface';

export interface ButtonProps {
  label: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
}

export function Button({
  label,
  onPress,
  variant = 'accent',
  disabled = false,
  loading = false,
}: ButtonProps) {
  const { tokens } = useTheme();
  const isInteractionBlocked = disabled || loading;

  const backgroundColor = variant === 'accent' ? tokens.accent : tokens.surface;
  const textVariant = variant === 'accent' ? 'onAccent' : 'primary';
  const indicatorColor = variant === 'accent' ? tokens.accentText : tokens.textPrimary;

  return (
    <TouchableOpacity
      style={[styles.button, { backgroundColor }, isInteractionBlocked && styles.blocked]}
      onPress={onPress}
      disabled={isInteractionBlocked}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled: isInteractionBlocked, busy: loading }}
    >
      {loading ? (
        <ActivityIndicator color={indicatorColor} />
      ) : (
        <ThemedText variant={textVariant} style={styles.label}>
          {label}
        </ThemedText>
      )}
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  button: {
    paddingVertical: 14,
    paddingHorizontal: 24,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: 48,
  },
  blocked: {
    opacity: 0.6,
  },
  label: {
    fontSize: 16,
    fontWeight: '700',
  },
});

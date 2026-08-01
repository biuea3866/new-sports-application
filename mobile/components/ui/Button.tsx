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
  // surface variant는 같은 surface 배경의 Card 위에 놓이는 일이 많아(모집 신청 내역의 "취소")
  // 배경색만으로는 버튼 경계가 사라진다 — 테두리로 탭 가능한 영역을 드러낸다.
  const borderStyle =
    variant === 'accent' ? { borderWidth: 0 } : { borderWidth: 1, borderColor: tokens.border };

  return (
    <TouchableOpacity
      style={[
        styles.button,
        { backgroundColor },
        borderStyle,
        isInteractionBlocked && styles.blocked,
      ]}
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

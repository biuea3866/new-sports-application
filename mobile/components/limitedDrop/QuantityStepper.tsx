/**
 * QuantityStepper — 구매 수량을 증감하는 프레젠테이션 컴포넌트.
 * max는 회차의 perUserLimit(1인당 최대 구매 수량)을 그대로 전달받습니다.
 * 경계(min/max)에서 해당 버튼을 disabled 처리합니다. 색은 항상 useTheme() 토큰을 경유합니다.
 */
import { StyleSheet, TouchableOpacity, View } from 'react-native';
import { useTheme } from '../../theme/useTheme';
import { ThemedText } from '../themed/ThemedText';

export interface QuantityStepperProps {
  value: number;
  /** 1인당 최대 구매 수량(perUserLimit) */
  max: number;
  /** 최소 수량, 기본값 1 */
  min?: number;
  onChange: (nextValue: number) => void;
}

export function QuantityStepper({ value, max, min = 1, onChange }: QuantityStepperProps) {
  const { tokens } = useTheme();
  const canDecrease = value > min;
  const canIncrease = value < max;

  const handleDecrease = () => {
    if (!canDecrease) {
      return;
    }
    onChange(value - 1);
  };

  const handleIncrease = () => {
    if (!canIncrease) {
      return;
    }
    onChange(value + 1);
  };

  return (
    <View style={styles.container}>
      <TouchableOpacity
        style={[styles.button, { borderColor: tokens.border }]}
        onPress={handleDecrease}
        disabled={!canDecrease}
        accessibilityRole="button"
        accessibilityLabel="수량 감소"
        accessibilityState={{ disabled: !canDecrease }}
      >
        <ThemedText variant={canDecrease ? 'primary' : 'muted'} style={styles.buttonLabel}>
          −
        </ThemedText>
      </TouchableOpacity>

      <ThemedText variant="primary" style={styles.value}>
        {value}
      </ThemedText>

      <TouchableOpacity
        style={[styles.button, { borderColor: tokens.border }]}
        onPress={handleIncrease}
        disabled={!canIncrease}
        accessibilityRole="button"
        accessibilityLabel="수량 증가"
        accessibilityState={{ disabled: !canIncrease }}
      >
        <ThemedText variant={canIncrease ? 'primary' : 'muted'} style={styles.buttonLabel}>
          +
        </ThemedText>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 16,
  },
  button: {
    width: 36,
    height: 36,
    borderWidth: 1,
    borderRadius: 8,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonLabel: {
    fontSize: 18,
    fontWeight: '700',
  },
  value: {
    fontSize: 16,
    fontWeight: '600',
    minWidth: 24,
    textAlign: 'center',
  },
});

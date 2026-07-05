/**
 * SegmentedControl — 공개/비공개·발화권한 등 옵션 중 하나를 선택하는 세그먼트 컨트롤.
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { Pressable, View, StyleSheet } from 'react-native';
import { useTheme } from '../../theme/useTheme';
import { ThemedText } from '../themed/ThemedText';

export interface SegmentedControlOption {
  label: string;
  value: string;
}

export interface SegmentedControlProps {
  options: SegmentedControlOption[];
  value: string;
  onChange: (value: string) => void;
}

export function SegmentedControl({ options, value, onChange }: SegmentedControlProps) {
  const { tokens } = useTheme();

  return (
    <View style={[styles.container, { backgroundColor: tokens.surface }]}>
      {options.map((option) => {
        const isSelected = option.value === value;

        return (
          <Pressable
            key={option.value}
            style={[styles.segment, isSelected && { backgroundColor: tokens.surfaceElevated }]}
            onPress={() => onChange(option.value)}
            accessibilityRole="button"
            accessibilityLabel={option.label}
            accessibilityState={{ selected: isSelected }}
          >
            <ThemedText variant={isSelected ? 'accent' : 'secondary'} style={styles.label}>
              {option.label}
            </ThemedText>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    borderRadius: 10,
    padding: 4,
  },
  segment: {
    flex: 1,
    paddingVertical: 8,
    alignItems: 'center',
    borderRadius: 8,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
  },
});

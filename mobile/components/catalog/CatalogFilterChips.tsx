/**
 * CatalogFilterChips — 통합 검색 화면(`/catalog`)의 가로 스크롤 필터 칩.
 *
 * 균등분할(flex) 세그먼트 컨트롤을 폭이 정해지지 않은 가로 ScrollView 안에 넣으면 각 칸이
 * 최소 폭으로 찌그러져 "한정판"이 한정/판으로 쪼개지고 선택 칩 배경이 글자를 못 덮는다
 * (유즈케이스 캡쳐 11-통합-카탈로그). 그래서 칩을 내용 크기로 잡고 라벨을 한 줄로 고정한다.
 *
 * 색은 항상 useTheme() 토큰을 경유한다(하드코딩 색 없음).
 */
import { Pressable, ScrollView, StyleSheet } from 'react-native';

import { useTheme } from '../../theme/useTheme';
import { ThemedText } from '../themed/ThemedText';

export interface CatalogFilterChipOption {
  label: string;
  value: string;
}

export interface CatalogFilterChipsProps {
  options: CatalogFilterChipOption[];
  value: string;
  onChange: (value: string) => void;
  accessibilityLabel: string;
}

export function CatalogFilterChips({
  options,
  value,
  onChange,
  accessibilityLabel,
}: CatalogFilterChipsProps) {
  const { tokens } = useTheme();

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.scroll}
      contentContainerStyle={styles.content}
      accessibilityLabel={accessibilityLabel}
    >
      {options.map((option) => {
        const isSelected = option.value === value;
        return (
          <Pressable
            key={option.value}
            onPress={() => onChange(option.value)}
            accessibilityRole="button"
            accessibilityLabel={option.label}
            accessibilityState={{ selected: isSelected }}
            style={[
              styles.chip,
              {
                backgroundColor: isSelected ? tokens.accent : tokens.surface,
                borderColor: isSelected ? tokens.accent : tokens.border,
              },
            ]}
          >
            <ThemedText
              variant={isSelected ? 'onAccent' : 'secondary'}
              style={styles.label}
              numberOfLines={1}
            >
              {option.label}
            </ThemedText>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  scroll: {
    flexGrow: 0,
  },
  content: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 4,
    paddingRight: 8,
  },
  chip: {
    // 내용 크기로 잡히도록 flex 를 쓰지 않는다 — 라벨이 잘리거나 줄바꿈되지 않게 한다.
    flexShrink: 0,
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: StyleSheet.hairlineWidth,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
  },
});

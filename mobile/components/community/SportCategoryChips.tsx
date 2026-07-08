/**
 * SportCategoryChips — 종목 선택 가로 스크롤 칩(토스 카테고리 칩 패턴).
 *
 * 근거: design-fe-app.md "텍스트 와이어프레임" A-P5("[전체][⚽축구][🏀농구]..." 가로 스크롤,
 * 선택 칩만 accent). `allLabel` 지정 시 맨 앞에 "전체"(null 선택) 칩을 추가한다 —
 * 전역 게시글 필터(A-P5)는 전체 옵션이 필요하고, 개설 폼 종목 선택(A-P3)은 불필요하다.
 */
import { ScrollView, Pressable, StyleSheet } from 'react-native';

import type { SportCategory } from '../../api/community-types';
import { SPORT_CATEGORY_OPTIONS } from '../../lib/community-format';
import { ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';

export interface SportCategoryChipsProps {
  selected: SportCategory | null;
  onSelect: (value: SportCategory | null) => void;
  /** 지정하면 맨 앞에 전체(null) 선택 칩을 추가한다. */
  allLabel?: string;
}

export function SportCategoryChips({ selected, onSelect, allLabel }: SportCategoryChipsProps) {
  const { tokens } = useTheme();

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.container}
      accessibilityLabel="종목 선택"
    >
      {allLabel !== undefined && (
        <Pressable
          style={[
            styles.chip,
            { borderColor: tokens.border },
            selected === null && { backgroundColor: tokens.accent, borderColor: tokens.accent },
          ]}
          onPress={() => onSelect(null)}
          accessibilityRole="button"
          accessibilityLabel={allLabel}
          accessibilityState={{ selected: selected === null }}
        >
          <ThemedText variant={selected === null ? 'onAccent' : 'secondary'} style={styles.label}>
            {allLabel}
          </ThemedText>
        </Pressable>
      )}
      {SPORT_CATEGORY_OPTIONS.map((option) => {
        const isSelected = option.value === selected;
        return (
          <Pressable
            key={option.value}
            style={[
              styles.chip,
              { borderColor: tokens.border },
              isSelected && { backgroundColor: tokens.accent, borderColor: tokens.accent },
            ]}
            onPress={() => onSelect(option.value)}
            accessibilityRole="button"
            accessibilityLabel={option.label}
            accessibilityState={{ selected: isSelected }}
          >
            <ThemedText variant={isSelected ? 'onAccent' : 'secondary'} style={styles.label}>
              {option.label}
            </ThemedText>
          </Pressable>
        );
      })}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    gap: 8,
    paddingVertical: 4,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 20,
    borderWidth: 1.5,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
  },
});

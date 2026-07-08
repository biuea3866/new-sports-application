/**
 * CatalogSearchControls — 통합 검색 화면(`/catalog`) 상단의 검색 입력 + itemType/sellerType
 * 세그먼트 필터. 순수 프레젠테이션 컴포넌트로, 확정된 값 변경만 상위(CatalogScreen)로 콜백한다.
 * 근거: FE-08 티켓, design-fe-app.md 와이어프레임 ①·컴포넌트 트리.
 *
 * 검색어는 300ms 디바운스 후 onKeywordChange를 호출한다(디바운스 로직은 useDebouncedValue로 분리
 * — 컴포넌트 내 타이머 로직 최소화). 검색어·세그먼트 값은 지역 상태만 다루고 전역 승격하지 않는다
 * (no-global-by-default).
 *
 * 색은 항상 useTheme() 토큰을 경유한다(하드코딩 색 없음).
 */
import { useEffect, useRef, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, TextInput, View } from 'react-native';

import type { CatalogItemType, SellerType } from '../../api/catalog-types';
import { useDebouncedValue } from '../../lib/useDebouncedValue';
import { useTheme } from '../../theme/useTheme';
import { ThemedText } from '../themed/ThemedText';
import { SegmentedControl, type SegmentedControlOption } from '../ui/SegmentedControl';

const KEYWORD_DEBOUNCE_MS = 300;

const ALL_VALUE = 'ALL';

const ITEM_TYPE_OPTIONS: SegmentedControlOption[] = [
  { label: '전체', value: ALL_VALUE },
  { label: '상품', value: 'PRODUCT' },
  { label: '한정판', value: 'LIMITED_DROP' },
  { label: '티켓', value: 'TICKET' },
  { label: '클래스', value: 'PROGRAM' },
  { label: '모집', value: 'RECRUITMENT' },
];

const SELLER_TYPE_OPTIONS: SegmentedControlOption[] = [
  { label: '전체', value: ALL_VALUE },
  { label: '중고', value: 'B2C' },
  { label: '브랜드', value: 'B2B' },
];

export interface CatalogSearchControlsProps {
  keyword: string;
  onKeywordChange: (keyword: string) => void;
  itemType: CatalogItemType | undefined;
  onItemTypeChange: (itemType: CatalogItemType | undefined) => void;
  sellerType: SellerType | undefined;
  onSellerTypeChange: (sellerType: SellerType | undefined) => void;
}

export function CatalogSearchControls({
  keyword,
  onKeywordChange,
  itemType,
  onItemTypeChange,
  sellerType,
  onSellerTypeChange,
}: CatalogSearchControlsProps) {
  const { tokens } = useTheme();
  const [inputValue, setInputValue] = useState(keyword);
  const debouncedInputValue = useDebouncedValue(inputValue, KEYWORD_DEBOUNCE_MS);

  const onKeywordChangeRef = useRef(onKeywordChange);
  useEffect(() => {
    onKeywordChangeRef.current = onKeywordChange;
  }, [onKeywordChange]);

  const isFirstDebounce = useRef(true);
  useEffect(() => {
    if (isFirstDebounce.current) {
      isFirstDebounce.current = false;
      return;
    }
    onKeywordChangeRef.current(debouncedInputValue);
  }, [debouncedInputValue]);

  function handleClear() {
    setInputValue('');
    onKeywordChangeRef.current('');
  }

  function handleItemTypeChange(value: string) {
    onItemTypeChange(value === ALL_VALUE ? undefined : (value as CatalogItemType));
  }

  function handleSellerTypeChange(value: string) {
    onSellerTypeChange(value === ALL_VALUE ? undefined : (value as SellerType));
  }

  return (
    <View style={styles.container}>
      <View
        testID="catalog-search-input"
        style={[
          styles.inputRow,
          { backgroundColor: tokens.surfaceElevated, borderColor: tokens.border },
        ]}
      >
        <ThemedText variant="secondary" style={styles.searchIcon}>
          🔍
        </ThemedText>
        <TextInput
          value={inputValue}
          onChangeText={setInputValue}
          placeholder="상품, 티켓, 클래스 검색"
          placeholderTextColor={tokens.textTertiary}
          style={[styles.input, { color: tokens.textPrimary }]}
          accessibilityLabel="검색어 입력"
        />
        {inputValue.length > 0 && (
          <Pressable
            onPress={handleClear}
            accessibilityRole="button"
            accessibilityLabel="검색어 지우기"
            style={styles.clearButton}
          >
            <ThemedText variant="secondary">✕</ThemedText>
          </Pressable>
        )}
      </View>

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={styles.segmentScroll}>
        <SegmentedControl
          options={ITEM_TYPE_OPTIONS}
          value={itemType ?? ALL_VALUE}
          onChange={handleItemTypeChange}
        />
      </ScrollView>

      {itemType === 'PRODUCT' && (
        <SegmentedControl
          options={SELLER_TYPE_OPTIONS}
          value={sellerType ?? ALL_VALUE}
          onChange={handleSellerTypeChange}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingHorizontal: 16,
    paddingTop: 12,
    gap: 8,
  },
  inputRow: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: StyleSheet.hairlineWidth,
    borderRadius: 10,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  searchIcon: {
    marginRight: 8,
    fontSize: 14,
  },
  input: {
    flex: 1,
    fontSize: 15,
    paddingVertical: 2,
  },
  clearButton: {
    paddingLeft: 8,
    paddingVertical: 4,
  },
  segmentScroll: {
    flexGrow: 0,
  },
});

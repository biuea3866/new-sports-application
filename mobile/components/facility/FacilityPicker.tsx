/**
 * FacilityPicker — 시설을 목록에서 고르는 선택기.
 *
 * 사용자에게 시설 내부 식별자(ID) 입력을 요구하지 않기 위한 컴포넌트다.
 * BE에 키워드 검색 API가 없어(`GET /facilities`는 지역·종류 필터만 지원) 이름·지역 검색은
 * 불러온 목록에 대한 클라이언트 필터로 처리한다.
 *
 * 프레젠테이션만 담당한다 — 조회는 호출부(`useFacilityOptions`)가, 필터 판정은
 * `lib/facility-format`의 순수 함수가 맡는다(no-logic-in-component).
 */
import { useState } from 'react';
import { FlatList, Pressable, StyleSheet, TextInput, View } from 'react-native';

import type { FacilityResponse } from '../../api/types';
import { EmptyState, ErrorView, LoadingView, ThemedText } from '../ui';
import { filterFacilitiesByKeyword, formatFacilityMetaLine } from '../../lib/facility-format';
import { useTheme } from '../../theme/useTheme';

const SEARCH_PLACEHOLDER = '시설 이름 · 지역으로 검색';
const NO_MATCH_MESSAGE = '조건에 맞는 시설이 없어요';
const LOAD_ERROR_MESSAGE = '시설 목록을 불러오지 못했어요';

export interface FacilityPickerProps {
  facilities: FacilityResponse[] | undefined;
  isLoading: boolean;
  isError: boolean;
  onRetry: () => void;
  onSelect: (facility: FacilityResponse) => void;
}

export function FacilityPicker({
  facilities,
  isLoading,
  isError,
  onRetry,
  onSelect,
}: FacilityPickerProps) {
  const { tokens } = useTheme();
  const [keyword, setKeyword] = useState('');

  if (isLoading) {
    return <LoadingView variant="skeleton" />;
  }

  if (isError) {
    return <ErrorView message={LOAD_ERROR_MESSAGE} onRetry={onRetry} />;
  }

  const matchedFacilities = filterFacilitiesByKeyword(facilities ?? [], keyword);

  return (
    <View style={styles.container}>
      <TextInput
        value={keyword}
        onChangeText={setKeyword}
        placeholder={SEARCH_PLACEHOLDER}
        placeholderTextColor={tokens.textTertiary}
        accessibilityLabel="시설 검색"
        style={[
          styles.search,
          {
            borderColor: tokens.border,
            color: tokens.textPrimary,
            backgroundColor: tokens.surfaceElevated,
          },
        ]}
      />

      {matchedFacilities.length === 0 ? (
        <EmptyState message={NO_MATCH_MESSAGE} />
      ) : (
        <FlatList
          data={matchedFacilities}
          keyExtractor={(item) => item.id}
          renderItem={({ item }) => (
            <Pressable
              style={[
                styles.option,
                { borderColor: tokens.border, backgroundColor: tokens.surface },
              ]}
              onPress={() => onSelect(item)}
              accessibilityRole="button"
              accessibilityLabel={`${item.name}, ${formatFacilityMetaLine(item)}`}
            >
              <ThemedText variant="primary" style={styles.optionName} numberOfLines={1}>
                {item.name}
              </ThemedText>
              <ThemedText variant="secondary" style={styles.optionMeta} numberOfLines={1}>
                {formatFacilityMetaLine(item)}
              </ThemedText>
            </Pressable>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  search: {
    borderWidth: 1,
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 16,
    marginBottom: 12,
  },
  option: {
    borderWidth: 1,
    borderRadius: 10,
    padding: 14,
    marginBottom: 8,
  },
  optionName: {
    fontSize: 15,
    fontWeight: '600',
  },
  optionMeta: {
    fontSize: 12,
    marginTop: 2,
  },
});

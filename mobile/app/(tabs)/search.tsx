/**
 * 시설 검색 탭 — MO-07
 * GET /facilities?gu={gu}&type={type}&page=0&size=50 (public)
 */
import { useState, useCallback } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  TouchableOpacity,
  ActivityIndicator,
  StyleSheet,
} from 'react-native';
import { router } from 'expo-router';
import { useFacilities } from '../../lib/useFacility';
import { ROUTES } from '../../lib/navigation';
import type { FacilityResponse, FacilityType } from '../../api/types';

const FACILITY_TYPES: { label: string; value: FacilityType }[] = [
  { label: '실내', value: 'INDOOR' },
  { label: '실외', value: 'OUTDOOR' },
  { label: '복합', value: 'MIXED' },
];

interface FacilityCardProps {
  facility: FacilityResponse;
  onPress: () => void;
}

function FacilityCard({ facility, onPress }: FacilityCardProps) {
  const typeLabel =
    FACILITY_TYPES.find((t) => t.value === facility.type)?.label ?? facility.type;

  return (
    <TouchableOpacity
      style={styles.card}
      onPress={onPress}
      accessible={true}
      accessibilityLabel={`${facility.name} 시설 상세 보기`}
      accessibilityRole="button"
    >
      <Text style={styles.cardName}>{facility.name}</Text>
      <View style={styles.cardMeta}>
        <Text style={styles.cardMetaText}>{facility.gu}</Text>
        <Text style={styles.cardDot}> · </Text>
        <Text style={styles.cardMetaText}>{typeLabel}</Text>
        {facility.parking && (
          <>
            <Text style={styles.cardDot}> · </Text>
            <Text style={styles.cardMetaText}>주차 가능</Text>
          </>
        )}
      </View>
      <Text style={styles.cardAddress}>{facility.address}</Text>
      {facility.phone.length > 0 && (
        <Text style={styles.cardPhone}>{facility.phone}</Text>
      )}
    </TouchableOpacity>
  );
}

export default function SearchScreen() {
  const [guInput, setGuInput] = useState('');
  const [selectedType, setSelectedType] = useState<FacilityType | undefined>(undefined);

  const { data, isLoading, isError, error } = useFacilities({
    gu: guInput.trim(),
    type: selectedType,
  });

  const handleCardPress = useCallback((id: number) => {
    router.push(ROUTES.facility.detail(String(id)));
  }, []);

  const handleTypeChip = useCallback(
    (type: FacilityType) => {
      setSelectedType((prev) => (prev === type ? undefined : type));
    },
    []
  );

  return (
    <View style={styles.container} accessible={false}>
      <View style={styles.searchSection}>
        <TextInput
          style={styles.input}
          placeholder="구 이름으로 검색 (예: 강남구)"
          placeholderTextColor="#8E8E93"
          value={guInput}
          onChangeText={setGuInput}
          returnKeyType="search"
          accessible={true}
          accessibilityLabel="구 이름 입력"
          accessibilityHint="구 이름을 입력하면 해당 구의 시설을 검색합니다"
        />
        <View style={styles.chipRow} accessible={false}>
          {FACILITY_TYPES.map(({ label, value }) => (
            <TouchableOpacity
              key={value}
              style={[styles.chip, selectedType === value && styles.chipSelected]}
              onPress={() => handleTypeChip(value)}
              accessible={true}
              accessibilityLabel={`${label} 타입 필터`}
              accessibilityState={{ selected: selectedType === value }}
              accessibilityRole="button"
            >
              <Text
                style={[styles.chipText, selectedType === value && styles.chipTextSelected]}
              >
                {label}
              </Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>

      {isLoading && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="검색 중">
          <ActivityIndicator size="large" color="#007AFF" />
        </View>
      )}

      {isError && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="오류 발생">
          <Text style={styles.errorText}>
            {error instanceof Error ? error.message : '검색에 실패했습니다.'}
          </Text>
        </View>
      )}

      {!isLoading && !isError && data !== undefined && (
        <FlatList
          data={data.content}
          keyExtractor={(item) => String(item.id)}
          renderItem={({ item }) => (
            <FacilityCard facility={item} onPress={() => handleCardPress(item.id)} />
          )}
          ListEmptyComponent={
            <View style={styles.centerBox} accessible={true} accessibilityLabel="검색 결과 없음">
              <Text style={styles.emptyText}>검색 결과가 없습니다.</Text>
            </View>
          }
          contentContainerStyle={
            data.content.length === 0 ? styles.listEmpty : styles.listContent
          }
          accessibilityLabel="시설 검색 결과 목록"
        />
      )}

      {!isLoading && !isError && data === undefined && (
        <View style={styles.centerBox} accessible={true} accessibilityLabel="검색 안내">
          <Text style={styles.emptyText}>구 이름 또는 시설 타입으로 검색하세요.</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F2F2F7',
  },
  searchSection: {
    backgroundColor: '#fff',
    paddingHorizontal: 16,
    paddingTop: 56,
    paddingBottom: 12,
  },
  input: {
    height: 44,
    borderRadius: 10,
    backgroundColor: '#F2F2F7',
    paddingHorizontal: 12,
    fontSize: 16,
    color: '#1C1C1E',
    marginBottom: 10,
  },
  chipRow: {
    flexDirection: 'row',
    gap: 8,
  },
  chip: {
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#C7C7CC',
    backgroundColor: '#fff',
  },
  chipSelected: {
    backgroundColor: '#007AFF',
    borderColor: '#007AFF',
  },
  chipText: {
    fontSize: 14,
    color: '#3A3A3C',
  },
  chipTextSelected: {
    color: '#fff',
  },
  centerBox: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  errorText: {
    fontSize: 14,
    color: '#FF3B30',
    textAlign: 'center',
  },
  emptyText: {
    fontSize: 14,
    color: '#8E8E93',
    textAlign: 'center',
  },
  listContent: {
    padding: 12,
    gap: 10,
  },
  listEmpty: {
    flex: 1,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 10,
  },
  cardName: {
    fontSize: 16,
    fontWeight: '600',
    color: '#1C1C1E',
    marginBottom: 4,
  },
  cardMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 4,
  },
  cardMetaText: {
    fontSize: 13,
    color: '#636366',
  },
  cardDot: {
    fontSize: 13,
    color: '#C7C7CC',
  },
  cardAddress: {
    fontSize: 13,
    color: '#636366',
    marginTop: 2,
  },
  cardPhone: {
    fontSize: 13,
    color: '#007AFF',
    marginTop: 4,
  },
});

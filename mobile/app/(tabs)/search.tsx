/**
 * 시설 검색 탭 — GET /facilities 브라우즈.
 * 구(gu) 키워드로 필터링하고 목록을 표시한다.
 */
import { useState } from 'react';
import {
  View,
  Text,
  TextInput,
  FlatList,
  Pressable,
  StyleSheet,
  ActivityIndicator,
} from 'react-native';
import { useQuery } from '@tanstack/react-query';
import { getBeClient } from '../../api/be-client';

interface Facility {
  id: string;
  name: string;
  gu: string;
  type: string;
  address: string;
  parking: boolean;
  tel: string;
}

interface FacilityPage {
  content: Facility[];
  totalElements: number;
}

async function fetchFacilities(gu: string): Promise<FacilityPage> {
  const params = new URLSearchParams({ page: '0', size: '50' });
  if (gu.trim()) params.set('gu', gu.trim());
  const res = await getBeClient().get<FacilityPage>(`/facilities?${params.toString()}`);
  return res.data;
}

export default function SearchScreen() {
  const [guInput, setGuInput] = useState('');
  const [query, setQuery] = useState('');

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['facilities', query],
    queryFn: () => fetchFacilities(query),
  });

  return (
    <View style={styles.container} accessibilityLabel="시설 검색 화면">
      <Text style={styles.title}>시설 검색</Text>

      <View style={styles.searchRow}>
        <TextInput
          style={styles.input}
          placeholder="구 이름 (예: 강남구)"
          value={guInput}
          onChangeText={setGuInput}
          onSubmitEditing={() => setQuery(guInput)}
          accessibilityLabel="구 검색 입력"
        />
        <Pressable
          style={styles.searchButton}
          onPress={() => setQuery(guInput)}
          accessibilityRole="button"
          accessibilityLabel="검색"
        >
          <Text style={styles.searchButtonText}>검색</Text>
        </Pressable>
      </View>

      {isLoading && <ActivityIndicator style={styles.center} color="#007AFF" />}

      {isError && (
        <View style={styles.center}>
          <Text style={styles.error}>시설을 불러오지 못했습니다.</Text>
          <Pressable onPress={() => void refetch()} accessibilityRole="button">
            <Text style={styles.link}>다시 시도</Text>
          </Pressable>
        </View>
      )}

      {data && (
        <FlatList
          data={data.content}
          keyExtractor={(item) => item.id}
          ListHeaderComponent={<Text style={styles.count}>{data.totalElements}개 시설</Text>}
          ListEmptyComponent={<Text style={styles.empty}>검색 결과가 없습니다.</Text>}
          renderItem={({ item }) => (
            <View style={styles.facilityCard}>
              <Text style={styles.facilityName}>{item.name}</Text>
              <Text style={styles.facilityMeta}>
                {item.gu} · {item.type}
              </Text>
              <Text style={styles.facilityAddress}>{item.address}</Text>
              <Text style={styles.facilityMeta}>
                {item.parking ? '주차 가능' : '주차 불가'} · {item.tel}
              </Text>
            </View>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', padding: 16, paddingTop: 56 },
  title: { fontSize: 24, fontWeight: 'bold', color: '#1C1C1E', marginBottom: 16 },
  searchRow: { flexDirection: 'row', gap: 8, marginBottom: 16 },
  input: {
    flex: 1,
    borderWidth: 1,
    borderColor: '#D1D1D6',
    borderRadius: 8,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
  },
  searchButton: {
    backgroundColor: '#007AFF',
    borderRadius: 8,
    paddingHorizontal: 18,
    justifyContent: 'center',
  },
  searchButtonText: { color: '#fff', fontWeight: '600' },
  center: { alignItems: 'center', marginTop: 40 },
  error: { color: '#FF3B30', marginBottom: 8 },
  link: { color: '#007AFF', fontWeight: '600' },
  count: { fontSize: 13, color: '#8E8E93', marginBottom: 8 },
  empty: { textAlign: 'center', color: '#8E8E93', marginTop: 40 },
  facilityCard: {
    borderWidth: 1,
    borderColor: '#E5E5EA',
    borderRadius: 10,
    padding: 14,
    marginBottom: 10,
  },
  facilityName: { fontSize: 16, fontWeight: '600', color: '#1C1C1E' },
  facilityMeta: { fontSize: 13, color: '#8E8E93', marginTop: 2 },
  facilityAddress: { fontSize: 14, color: '#3A3A3C', marginTop: 4 },
});

/**
 * 시설 검색 탭 — 내 주변 시설 + 날씨.
 *
 * 모든 데이터는 우리 backend WAS 를 경유한다(외부 Kakao/기상청 직접 호출 금지).
 * - GET /facilities/near : 좌표 기반 시설 조회(BE GeoSpatial)
 * - GET /weather         : BE 가 기상청 단기예보 조회
 *
 * 기기 GPS(expo-location)는 네이티브 의존이므로 기본 좌표(서울 강남)를 사용한다.
 * 위치 권한 연동은 후속 작업(expo-location + dev build).
 */
import { useQuery } from '@tanstack/react-query';
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from 'react-native';

import {
  getNearbyFacilities,
  getWeather,
  type FacilitySummary,
  type Forecast,
} from '../../api/external-features';

// 기본 좌표: 서울 강남(역삼). 후속으로 expo-location 의 현재 위치로 대체.
const DEFAULT_LAT = 37.4979;
const DEFAULT_LNG = 127.0276;
const DEFAULT_RADIUS_METERS = 3000;

const SKY_LABEL: Record<string, string> = {
  CLEAR: '맑음',
  MOSTLY_CLOUDY: '구름많음',
  CLOUDY: '흐림',
};

function currentSlot(forecast: Forecast | undefined) {
  return forecast?.slots?.[0];
}

export default function SearchScreen() {
  const facilitiesQuery = useQuery({
    queryKey: ['facilities', 'near', DEFAULT_LAT, DEFAULT_LNG, DEFAULT_RADIUS_METERS],
    queryFn: () => getNearbyFacilities(DEFAULT_LAT, DEFAULT_LNG, DEFAULT_RADIUS_METERS),
  });

  const weatherQuery = useQuery({
    queryKey: ['weather', DEFAULT_LAT, DEFAULT_LNG],
    queryFn: () => getWeather(DEFAULT_LAT, DEFAULT_LNG),
  });

  const slot = currentSlot(weatherQuery.data);

  return (
    <View style={styles.container} accessible accessibilityLabel="시설 검색 화면">
      <Text style={styles.title}>내 주변 시설</Text>

      <View style={styles.weatherCard} accessibilityLabel="현재 날씨">
        {weatherQuery.isLoading ? (
          <Text style={styles.weatherText}>날씨 불러오는 중…</Text>
        ) : slot ? (
          <Text style={styles.weatherText}>
            {slot.temperature != null ? `${slot.temperature}℃` : '-'}
            {slot.sky ? `  ${SKY_LABEL[slot.sky] ?? slot.sky}` : ''}
            {slot.precipitationProbability != null ? `  강수 ${slot.precipitationProbability}%` : ''}
          </Text>
        ) : (
          <Text style={styles.weatherText}>날씨 정보 없음</Text>
        )}
      </View>

      {facilitiesQuery.isLoading ? (
        <ActivityIndicator style={styles.center} />
      ) : facilitiesQuery.isError ? (
        <Text style={styles.error}>시설을 불러오지 못했습니다.</Text>
      ) : (
        <FlatList<FacilitySummary>
          data={facilitiesQuery.data ?? []}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          ListEmptyComponent={<Text style={styles.empty}>주변에 시설이 없습니다.</Text>}
          renderItem={({ item }) => (
            <View style={styles.row} accessibilityLabel={`${item.name} ${item.type}`}>
              <Text style={styles.rowName}>{item.name}</Text>
              <Text style={styles.rowMeta}>
                {item.gu} · {item.type}
                {item.parking ? ' · 주차가능' : ''}
              </Text>
              <Text style={styles.rowAddr}>{item.address}</Text>
            </View>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#fff', paddingTop: 24 },
  title: { fontSize: 22, fontWeight: 'bold', color: '#1C1C1E', paddingHorizontal: 16 },
  weatherCard: {
    margin: 16,
    padding: 16,
    backgroundColor: '#EAF2FF',
    borderRadius: 12,
  },
  weatherText: { fontSize: 16, color: '#0A3D91', fontWeight: '600' },
  center: { marginTop: 40 },
  list: { paddingHorizontal: 16, paddingBottom: 24 },
  row: {
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: '#E5E5EA',
  },
  rowName: { fontSize: 16, fontWeight: '600', color: '#1C1C1E' },
  rowMeta: { fontSize: 13, color: '#8E8E93', marginTop: 2 },
  rowAddr: { fontSize: 13, color: '#3A3A3C', marginTop: 2 },
  empty: { fontSize: 14, color: '#8E8E93', textAlign: 'center', marginTop: 40 },
  error: { fontSize: 14, color: '#FF3B30', textAlign: 'center', marginTop: 40 },
});

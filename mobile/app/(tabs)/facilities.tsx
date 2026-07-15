/**
 * 시설 탭 — 내 주변 시설 + 날씨.
 *
 * 근거: 사용자 피드백 "search 탭 네이밍이 이해 안 된다" — 이 탭은 실제로 내 주변
 * 시설 검색(`/facilities/near` + 날씨)이므로 라벨을 "시설"로, 라우트 파일명도
 * `search.tsx` → `facilities.tsx`로 바꿨다(루트 `app/facility/[id]` 상세 라우트와는
 * 세그먼트가 달라 충돌하지 않는다).
 *
 * 모든 데이터는 우리 backend WAS 를 경유한다(외부 Kakao/기상청 직접 호출 금지).
 * - GET /facilities/near : 좌표 기반 시설 조회(BE GeoSpatial)
 * - GET /weather         : BE 가 기상청 단기예보 조회
 *
 * 기기 GPS(expo-location)는 네이티브 의존이므로 기본 좌표(서울 강남)를 사용한다.
 * 위치 권한 연동은 후속 작업(expo-location + dev build).
 * 색은 항상 useTheme() 토큰을 경유합니다 (하드코딩 색 없음).
 */
import { useQuery } from '@tanstack/react-query';
import { ActivityIndicator, FlatList, StyleSheet, View } from 'react-native';

import {
  getNearbyFacilities,
  getWeather,
  type FacilitySummary,
  type Forecast,
} from '../../api/external-features';
import { ThemedText, ThemedView } from '../../components/ui';
import { useTheme } from '../../theme/useTheme';

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

export default function FacilitiesScreen() {
  const { tokens } = useTheme();
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
    <ThemedView style={styles.container} accessible accessibilityLabel="시설 화면">
      <ThemedText variant="primary" style={styles.title} accessibilityRole="header">
        내 주변 시설
      </ThemedText>

      <View
        style={[styles.weatherCard, { backgroundColor: tokens.surface }]}
        accessibilityLabel="현재 날씨"
      >
        {weatherQuery.isLoading ? (
          <ThemedText variant="secondary" style={styles.weatherText}>
            날씨 불러오는 중…
          </ThemedText>
        ) : slot ? (
          <ThemedText variant="accent" style={styles.weatherText}>
            {slot.temperature != null ? `${slot.temperature}℃` : '-'}
            {slot.sky ? `  ${SKY_LABEL[slot.sky] ?? slot.sky}` : ''}
            {slot.precipitationProbability != null
              ? `  강수 ${slot.precipitationProbability}%`
              : ''}
          </ThemedText>
        ) : (
          <ThemedText variant="secondary" style={styles.weatherText}>
            날씨 정보 없음
          </ThemedText>
        )}
      </View>

      {facilitiesQuery.isLoading ? (
        <ActivityIndicator style={styles.center} color={tokens.accent} />
      ) : facilitiesQuery.isError ? (
        <ThemedText variant="danger" style={styles.error}>
          시설을 불러오지 못했습니다.
        </ThemedText>
      ) : (
        <FlatList<FacilitySummary>
          data={facilitiesQuery.data ?? []}
          keyExtractor={(item) => item.id}
          contentContainerStyle={styles.list}
          ListEmptyComponent={
            <ThemedText variant="secondary" style={styles.empty}>
              주변에 시설이 없습니다.
            </ThemedText>
          }
          renderItem={({ item }) => (
            <View
              style={[styles.row, { borderBottomColor: tokens.border }]}
              accessibilityLabel={`${item.name} ${item.type}`}
            >
              <ThemedText variant="primary" style={styles.rowName}>
                {item.name}
              </ThemedText>
              <ThemedText variant="secondary" style={styles.rowMeta}>
                {item.gu} · {item.type}
                {item.parking ? ' · 주차가능' : ''}
              </ThemedText>
              <ThemedText variant="secondary" style={styles.rowAddr}>
                {item.address}
              </ThemedText>
            </View>
          )}
        />
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, paddingTop: 56 },
  title: { fontSize: 22, fontWeight: 'bold', paddingHorizontal: 16 },
  weatherCard: {
    margin: 16,
    padding: 16,
    borderRadius: 12,
  },
  weatherText: { fontSize: 16, fontWeight: '600' },
  center: { marginTop: 40 },
  list: { paddingHorizontal: 16, paddingBottom: 24 },
  row: {
    paddingVertical: 12,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  rowName: { fontSize: 16, fontWeight: '600' },
  rowMeta: { fontSize: 13, marginTop: 2 },
  rowAddr: { fontSize: 13, marginTop: 2 },
  empty: { fontSize: 14, textAlign: 'center', marginTop: 40 },
  error: { fontSize: 14, textAlign: 'center', marginTop: 40 },
});

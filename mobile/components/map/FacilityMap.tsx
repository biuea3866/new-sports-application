/**
 * FacilityMap.tsx — 네이티브(iOS/Android) 기본 지도. react-native-maps.
 *
 * Metro가 플랫폼 확장자를 해석할 때(.web.tsx 우선) 웹 번들에는 FacilityMap.web.tsx가
 * 대신 선택되므로, 이 파일은 네이티브 번들에만 포함된다. 두 구현 모두
 * components/map/types.ts의 동일한 FacilityMapProps를 받는다.
 */
import { StyleSheet, View } from 'react-native';
import MapView, { Marker } from 'react-native-maps';

import { ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';
import { filterValidFacilities, type FacilityMapProps } from './types';

const MAP_HEIGHT = 220;
const LATITUDE_DELTA = 0.05;
const LONGITUDE_DELTA = 0.05;

export function FacilityMap({ facilities, center, onMarkerPress }: FacilityMapProps) {
  const { tokens } = useTheme();
  const validFacilities = filterValidFacilities(facilities);

  if (validFacilities.length === 0) {
    return (
      <View
        style={[styles.fallback, { backgroundColor: tokens.surface }]}
        accessible
        accessibilityLabel="지도에 표시할 시설 없음"
      >
        <ThemedText variant="secondary">주변에 표시할 시설이 없어요</ThemedText>
      </View>
    );
  }

  return (
    <MapView
      style={styles.map}
      initialRegion={{
        latitude: center.lat,
        longitude: center.lng,
        latitudeDelta: LATITUDE_DELTA,
        longitudeDelta: LONGITUDE_DELTA,
      }}
      accessibilityLabel="주변 시설 지도"
    >
      {validFacilities.map((facility) => (
        <Marker
          key={facility.id}
          coordinate={{ latitude: facility.lat, longitude: facility.lng }}
          title={facility.name}
          onPress={() => onMarkerPress(facility.id)}
          accessibilityLabel={facility.name}
        />
      ))}
    </MapView>
  );
}

const styles = StyleSheet.create({
  map: {
    height: MAP_HEIGHT,
    borderRadius: 12,
  },
  fallback: {
    height: MAP_HEIGHT,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

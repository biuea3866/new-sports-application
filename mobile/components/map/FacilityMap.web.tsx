/**
 * FacilityMap.web.tsx — 웹(react-native-web) 전용 지도. Leaflet + OpenStreetMap 타일.
 *
 * Expo 웹 번들은 react-native-maps(네이티브 전용)를 로드할 수 없으므로 무료·API 키
 * 불필요한 Leaflet으로 별도 구현한다. Metro의 플랫폼 확장자 해석(.web.tsx 우선)이
 * 웹 번들에서 이 파일을, 네이티브 번들에서 FacilityMap.tsx(react-native-maps)를
 * 각각 선택한다 — 두 파일은 components/map/types.ts의 동일한 FacilityMapProps를 받는다.
 *
 * 시설 개수·좌표는 대부분 소규모(주변 반경 조회 결과)이므로 매 렌더마다 마커를
 * 새로 그리는 단순한 구현으로 충분하다(가상화 불필요).
 */
import { useEffect, useId, useRef } from 'react';
import { StyleSheet, View } from 'react-native';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';

import { ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';
import { filterValidFacilities, type FacilityMapProps } from './types';

const DEFAULT_ZOOM = 14;
const MAP_HEIGHT = 220;

/**
 * leaflet/dist/leaflet.css의 기본 마커 아이콘은 상대 경로 CSS `url(images/marker-icon.png)`로
 * 참조된다. Metro 웹 번들은 CSS 안의 로컬 리소스 import를 지원하지 않아("Importing local
 * resources in CSS is not supported yet") 이 아이콘이 깨진 이미지로 렌더된다. 설치된
 * leaflet 버전과 동일한 unpkg CDN 경로로 기본 아이콘을 재지정한다 — Leaflet+번들러(Webpack/
 * Vite/Metro) 조합에서 널리 쓰이는 표준 우회다.
 */
const LEAFLET_VERSION = '1.9.4';
const LEAFLET_CDN_IMAGES = `https://unpkg.com/leaflet@${LEAFLET_VERSION}/dist/images`;

L.Marker.prototype.options.icon = L.icon({
  iconUrl: `${LEAFLET_CDN_IMAGES}/marker-icon.png`,
  iconRetinaUrl: `${LEAFLET_CDN_IMAGES}/marker-icon-2x.png`,
  shadowUrl: `${LEAFLET_CDN_IMAGES}/marker-shadow.png`,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

export function FacilityMap({ facilities, center, onMarkerPress }: FacilityMapProps) {
  const { tokens } = useTheme();
  // Leaflet은 DOM id 문자열로 컨테이너를 찾는다(L.map(id)) — ref 콜백 대신 id를 쓰면
  // "커밋 이후에만 존재하는 실 DOM 노드"에 대한 의존을 없애 테스트 환경(react-test-renderer는
  // 호스트 컴포넌트 ref를 채우지 않음)에서도 동일한 초기화 경로를 검증할 수 있다.
  const mapElementId = `facility-map-${useId()}`;
  const mapRef = useRef<L.Map | null>(null);
  const markersRef = useRef<L.Marker[]>([]);

  const validFacilities = filterValidFacilities(facilities);
  const hasFacilities = validFacilities.length > 0;

  useEffect(() => {
    if (!hasFacilities) {
      return;
    }

    const map = L.map(mapElementId).setView([center.lat, center.lng], DEFAULT_ZOOM);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; OpenStreetMap contributors',
    }).addTo(map);
    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
    };
    // center는 최초 마운트 시점 기준으로만 지도를 생성한다 — 재조회로 center가 흔들려도
    // 지도를 매번 재생성하지 않는다(마커 effect가 별도로 갱신).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasFacilities, mapElementId]);

  useEffect(() => {
    // 웹 geolocation은 최초 렌더 이후 비동기로 resolve될 수 있다(useCurrentLocation.web) —
    // 지도를 재생성하지 않고 이미 만들어진 지도의 중심만 갱신한다.
    mapRef.current?.setView([center.lat, center.lng]);
  }, [center.lat, center.lng]);

  useEffect(() => {
    const map = mapRef.current;
    if (map === null) {
      return;
    }

    markersRef.current.forEach((marker) => marker.remove());
    markersRef.current = validFacilities.map((facility) => {
      const marker = L.marker([facility.lat, facility.lng]).addTo(map);
      marker.bindPopup(facility.name);
      marker.on('click', () => onMarkerPress(facility.id));
      return marker;
    });

    return () => {
      markersRef.current.forEach((marker) => marker.remove());
      markersRef.current = [];
    };
  }, [validFacilities, onMarkerPress]);

  if (!hasFacilities) {
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
    <View
      style={styles.mapWrapper}
      accessible
      accessibilityLabel="주변 시설 지도"
      testID="facility-map-web-wrapper"
    >
      {/* react-native-web은 raw DOM 엘리먼트를 그대로 렌더한다 — Leaflet이 id로 찾는 컨테이너. */}
      <div id={mapElementId} style={{ width: '100%', height: MAP_HEIGHT, borderRadius: 12 }} />
    </View>
  );
}

const styles = StyleSheet.create({
  mapWrapper: {
    height: MAP_HEIGHT,
    borderRadius: 12,
    overflow: 'hidden',
  },
  fallback: {
    height: MAP_HEIGHT,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

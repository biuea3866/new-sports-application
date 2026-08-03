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

import { ThemedText } from '../ui';
import { useTheme } from '../../theme/useTheme';
import {
  MAP_THEME_STYLE_ATTRIBUTE,
  buildDarkControlCss,
  mapContainerClassName,
} from './mapThemeCss';
import { filterValidFacilities, type FacilityMapProps } from './types';

const DEFAULT_ZOOM = 14;
const MAP_HEIGHT = 220;

/**
 * 지도 타일도 화면의 일부라 라이트/다크 두 벌이 필요하다(no-single-mode).
 * OSM 표준 타일은 밝은 톤뿐이라 다크 화면에서 지도만 하얗게 뜬다 — 라이트/다크 두 벌을
 * 제공하는 CARTO Basemaps(OSM 데이터 기반, 무료·API 키 불필요)를 사용한다.
 */
const TILE_URL_BY_SCHEME = {
  light: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
  dark: 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png',
} as const;
const TILE_ATTRIBUTION = '&copy; OpenStreetMap contributors &copy; CARTO';

/**
 * CARTO 타일이 응답하지 않으면 "빈 회색 판 위에 마커만" 뜨는 조용한 실패가 된다 —
 * 라이트/다크 구분은 없지만 항상 떠 있는 OSM 표준 타일로 한 번 폴백한다.
 */
const FALLBACK_TILE_URL = 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png';

/**
 * leaflet/dist/leaflet.css의 기본 마커 아이콘은 상대 경로 CSS `url(images/marker-icon.png)`로
 * 참조된다. Metro 웹 번들은 CSS 안의 로컬 리소스 import를 지원하지 않아("Importing local
 * resources in CSS is not supported yet") 이 아이콘이 깨진 이미지로 렌더된다. 설치된
 * leaflet 버전과 동일한 unpkg CDN 경로로 기본 아이콘을 재지정한다 — Leaflet+번들러(Webpack/
 * Vite/Metro) 조합에서 널리 쓰이는 표준 우회다.
 */
const LEAFLET_VERSION = '1.9.4';
const LEAFLET_CDN_IMAGES = `https://unpkg.com/leaflet@${LEAFLET_VERSION}/dist/images`;

type LeafletModule = typeof import('leaflet');
type LeafletMap = ReturnType<LeafletModule['map']>;
type LeafletMarker = ReturnType<LeafletModule['marker']>;
type LeafletTileLayer = ReturnType<LeafletModule['tileLayer']>;

let cachedLeaflet: LeafletModule | null = null;

/**
 * Leaflet을 **클라이언트에서만** 로드한다.
 *
 * Expo Router 웹은 페이지를 node에서 먼저 렌더하는데(window 없음) Leaflet은 임포트 시점에
 * window를 참조해 "window is not defined"로 앱 전체가 500이 된다. 이펙트(=클라이언트에서만
 * 실행) 안에서 지연 로드해 서버 렌더 경로에 라이브러리가 실리지 않게 한다.
 */
function loadLeaflet(): LeafletModule {
  if (cachedLeaflet !== null) {
    return cachedLeaflet;
  }
  /* eslint-disable @typescript-eslint/no-var-requires, global-require */
  const leafletModule = require('leaflet') as LeafletModule & { default?: LeafletModule };
  require('leaflet/dist/leaflet.css');
  /* eslint-enable @typescript-eslint/no-var-requires, global-require */
  const leaflet = leafletModule.default ?? leafletModule;

  leaflet.Marker.prototype.options.icon = leaflet.icon({
    iconUrl: `${LEAFLET_CDN_IMAGES}/marker-icon.png`,
    iconRetinaUrl: `${LEAFLET_CDN_IMAGES}/marker-icon-2x.png`,
    shadowUrl: `${LEAFLET_CDN_IMAGES}/marker-shadow.png`,
    iconSize: [25, 41],
    iconAnchor: [12, 41],
    popupAnchor: [1, -34],
    shadowSize: [41, 41],
  });

  cachedLeaflet = leaflet;
  return leaflet;
}

export function FacilityMap({ facilities, center, onMarkerPress }: FacilityMapProps) {
  const { tokens, scheme } = useTheme();
  // Leaflet은 DOM id 문자열로 컨테이너를 찾는다(L.map(id)) — ref 콜백 대신 id를 쓰면
  // "커밋 이후에만 존재하는 실 DOM 노드"에 대한 의존을 없애 테스트 환경(react-test-renderer는
  // 호스트 컴포넌트 ref를 채우지 않음)에서도 동일한 초기화 경로를 검증할 수 있다.
  const mapElementId = `facility-map-${useId()}`;
  const mapRef = useRef<LeafletMap | null>(null);
  const markersRef = useRef<LeafletMarker[]>([]);
  const tileLayerRef = useRef<LeafletTileLayer | null>(null);
  const hasFallenBackToStandardTilesRef = useRef(false);
  /** 타일 레이어에 실제로 적용돼 있는 URL — 같은 값을 다시 setUrl 하지 않기 위한 기준. */
  const appliedTileUrlRef = useRef<string | null>(null);

  const validFacilities = filterValidFacilities(facilities);
  const hasFacilities = validFacilities.length > 0;

  useEffect(() => {
    if (!hasFacilities) {
      return;
    }

    const leaflet = loadLeaflet();
    const map = leaflet.map(mapElementId).setView([center.lat, center.lng], DEFAULT_ZOOM);
    const tileLayer = leaflet.tileLayer(TILE_URL_BY_SCHEME[scheme], {
      attribution: TILE_ATTRIBUTION,
    });
    // 타일 CDN 장애 시 1회만 표준 OSM으로 갈아탄다(폴백 타일도 실패하면 무한 교체를 막는다).
    tileLayer.on('tileerror', () => {
      if (hasFallenBackToStandardTilesRef.current) {
        return;
      }
      hasFallenBackToStandardTilesRef.current = true;
      appliedTileUrlRef.current = FALLBACK_TILE_URL;
      tileLayer.setUrl(FALLBACK_TILE_URL);
    });
    tileLayer.addTo(map);
    tileLayerRef.current = tileLayer;
    appliedTileUrlRef.current = TILE_URL_BY_SCHEME[scheme];
    mapRef.current = map;

    return () => {
      map.remove();
      mapRef.current = null;
      tileLayerRef.current = null;
      appliedTileUrlRef.current = null;
      hasFallenBackToStandardTilesRef.current = false;
    };
    // center·scheme는 최초 마운트 시점 기준으로만 지도를 생성한다 — 재조회로 center가 흔들리거나
    // 사용자가 테마를 토글해도 지도를 재생성하지 않는다. 지도를 다시 만들면 사용자가 이동·확대해
    // 둔 위치가 초기값으로 되돌아간다(마커·중심·타일은 각각 별도 effect가 갱신).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasFacilities, mapElementId]);

  useEffect(() => {
    // 테마 전환은 타일 URL만 교체한다 — 지도 인스턴스는 유지해 현재 보고 있는 위치를 지킨다.
    // 폴백 상태에서는 CARTO로 되돌리지 않는다(장애가 끝났는지 알 수 없다).
    if (hasFallenBackToStandardTilesRef.current) {
      return;
    }
    const nextTileUrl = TILE_URL_BY_SCHEME[scheme];
    if (appliedTileUrlRef.current === nextTileUrl) {
      return;
    }
    appliedTileUrlRef.current = nextTileUrl;
    tileLayerRef.current?.setUrl(nextTileUrl);
  }, [scheme]);

  useEffect(() => {
    // 컨트롤 다크 규칙은 문서 전역에 한 벌만 둔다 — 지도가 여러 개 떠도 중복 주입되지 않게
    // 표식 속성으로 기존 태그를 찾아 내용만 갱신한다.
    if (typeof document === 'undefined') {
      return;
    }
    const existing = document.querySelector(`style[${MAP_THEME_STYLE_ATTRIBUTE}]`);
    const styleElement = existing ?? document.createElement('style');
    if (existing === null) {
      styleElement.setAttribute(MAP_THEME_STYLE_ATTRIBUTE, '');
      document.head.appendChild(styleElement);
    }
    styleElement.textContent = buildDarkControlCss(
      tokens.surface,
      tokens.textPrimary,
      tokens.border
    );
  }, [tokens.surface, tokens.textPrimary, tokens.border]);

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

    const leaflet = loadLeaflet();
    markersRef.current.forEach((marker) => marker.remove());
    markersRef.current = validFacilities.map((facility) => {
      const marker = leaflet.marker([facility.lat, facility.lng]).addTo(map);
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
      <div
        id={mapElementId}
        className={mapContainerClassName(scheme)}
        style={{ width: '100%', height: MAP_HEIGHT, borderRadius: 12 }}
      />
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

/**
 * components/map/types.ts — FacilityMap(웹/네이티브) 공통 인터페이스.
 *
 * 웹은 Leaflet(components/map/FacilityMap.web.tsx), 네이티브는 react-native-maps
 * (components/map/FacilityMap.tsx)로 구현이 갈리지만, 두 구현 모두 이 props를
 * 그대로 받아 동일하게 소비할 수 있어야 한다 (플랫폼 분기 — private-fe-convention
 * "웹/RN 공유 경계": 컴포넌트는 플랫폼별 작성, 타입만 공유).
 */

/** 지도에 마커로 표시할 시설 1건 — GET /facilities/near 응답(FacilitySummary)의 부분 집합. */
export interface MapFacility {
  id: string;
  name: string;
  lat: number;
  lng: number;
}

/** 지도 중심 좌표. */
export interface MapCenter {
  lat: number;
  lng: number;
}

export interface FacilityMapProps {
  /** 마커로 표시할 시설 목록. 좌표가 유효(finite)하지 않은 항목은 구현체가 걸러낸다. */
  facilities: MapFacility[];
  /** 지도 초기 중심 좌표. */
  center: MapCenter;
  /** 마커(또는 팝업) 탭 시 시설 id를 전달한다 — 호출부가 상세 화면 이동을 담당. */
  onMarkerPress: (facilityId: string) => void;
}

/** 유효한(finite) 위경도를 가진 시설만 남긴다 — 두 플랫폼 구현이 공유하는 방어 필터. */
export function filterValidFacilities(facilities: MapFacility[]): MapFacility[] {
  return facilities.filter(
    (facility) => Number.isFinite(facility.lat) && Number.isFinite(facility.lng)
  );
}

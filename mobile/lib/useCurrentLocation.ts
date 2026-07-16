/**
 * useCurrentLocation — 시설 지도(components/map/FacilityMap)의 중심 좌표 해석 훅(네이티브 기본).
 *
 * `app/(tabs)/facilities.tsx` 주석 "기기 GPS(expo-location)는 네이티브 의존이므로
 * 기본 좌표를 사용한다. 위치 권한 연동은 후속 작업"과 동일한 전제 — 네이티브는
 * 아직 위치 권한 연동 전이라 항상 호출부가 넘긴 기본 좌표를 그대로 반환한다.
 * 웹은 `useCurrentLocation.web.ts`가 `navigator.geolocation`으로 실제 위치를 조회한다
 * (Metro 플랫폼 확장자 해석으로 웹 번들만 그 파일을 사용).
 */
import type { MapCenter } from '../components/map/types';

export interface CurrentLocation extends MapCenter {
  /** true면 기기 위치가 아니라 호출부가 넘긴 기본 좌표를 쓰고 있다는 뜻. */
  isDefault: boolean;
}

export function useCurrentLocation(defaultCenter: MapCenter): CurrentLocation {
  return { ...defaultCenter, isDefault: true };
}

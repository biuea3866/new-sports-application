/**
 * useCurrentLocation.web.ts — 웹 전용 구현. navigator.geolocation으로 현재 위치를 조회하고,
 * 권한 거부·API 미지원·오류 시 기본 좌표로 폴백한다(design 지시 "권한 거부 시 서울시청 등
 * 기본 좌표로 폴백" — 이 화면은 기존 기본 좌표(강남)를 그대로 폴백 값으로 재사용한다).
 */
import { useEffect, useState } from 'react';
import type { MapCenter } from '../components/map/types';
import type { CurrentLocation } from './useCurrentLocation';

export type { CurrentLocation } from './useCurrentLocation';

export function useCurrentLocation(defaultCenter: MapCenter): CurrentLocation {
  const [location, setLocation] = useState<CurrentLocation>({
    ...defaultCenter,
    isDefault: true,
  });

  useEffect(() => {
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocation({
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          isDefault: false,
        });
      },
      () => {
        setLocation({ ...defaultCenter, isDefault: true });
      }
    );
    // defaultCenter는 화면 상수(재렌더에도 값이 고정)라 최초 1회만 요청한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return location;
}

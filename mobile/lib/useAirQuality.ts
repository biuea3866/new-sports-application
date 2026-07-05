/**
 * useAirQuality — GET /air-quality TanStack Query 훅
 *
 * 근거: design-fe-app.md "API 연동 표" · FE-12
 * 좌표(lat/lng)가 모두 확정되기 전에는 조회하지 않는다(enabled 가드).
 * BE가 실패 시에도 200 + UNKNOWN을 반환하므로 쿼리는 success로 취급하고,
 * 폴백 문구·경고 게이트 판단은 컴포넌트가 담당한다.
 */
import { useQuery } from '@tanstack/react-query';
import { getAirQuality } from '../api/air-quality';
import type { AirQualityResponse } from '../api/types';

export function useAirQuality(lat: number | null, lng: number | null) {
  return useQuery<AirQualityResponse, Error>({
    queryKey: ['air-quality', lat, lng],
    queryFn: () => {
      if (lat === null || lng === null) {
        return Promise.reject(new Error('좌표가 없습니다.'));
      }
      return getAirQuality(lat, lng);
    },
    enabled: lat !== null && lng !== null,
  });
}

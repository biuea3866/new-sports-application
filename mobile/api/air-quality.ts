/**
 * air-quality.ts — 대기질 조회 API 함수 (public, 인증 불필요)
 *
 * BE 계약: GET /air-quality?lat&lng → AirQualityResponse
 * BE는 조회 실패 시에도 200 + representativeGrade="UNKNOWN" + pm10/pm25 null로 응답한다
 * (BE TDD 실패 경로) — 이 함수는 그 응답을 그대로 데이터로 전달한다.
 */
import { getBeClient } from './be-client';
import type { AirQualityResponse } from './types';

export async function getAirQuality(lat: number, lng: number): Promise<AirQualityResponse> {
  const res = await getBeClient().get<AirQualityResponse>('/air-quality', {
    params: { lat, lng },
  });
  return res.data;
}

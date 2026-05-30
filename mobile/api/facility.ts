/**
 * facility.ts — 시설 API 함수 (public, 인증 불필요)
 */
import { getBeClient } from './be-client';
import type { FacilityPageResponse, FacilityResponse, FacilityType } from './types';

export interface FacilitySearchParams {
  gu?: string;
  type?: FacilityType;
  page?: number;
  size?: number;
}

export async function searchFacilities(
  params: FacilitySearchParams
): Promise<FacilityPageResponse> {
  const res = await getBeClient().get<FacilityPageResponse>('/facilities', { params });
  return res.data;
}

export async function getFacility(id: string): Promise<FacilityResponse> {
  const res = await getBeClient().get<FacilityResponse>(`/facilities/${id}`);
  return res.data;
}

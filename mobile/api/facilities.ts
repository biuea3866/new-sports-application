/**
 * facilities.ts — 시설 도메인 API 함수
 *
 * BE 경로:
 *   GET  /facilities           — 시설 목록 (검색 파라미터 포함)
 *   GET  /facilities/{id}      — 시설 상세
 *   GET  /facilities/{id}/slots — 시설 예약 가능 슬롯 목록
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export interface FacilityListParams {
  keyword?: string;
  category?: string;
  page?: number;
  size?: number;
}

export interface FacilityDto {
  id: number;
  name: string;
  category: string;
  address: string;
  description: string;
  imageUrl: string | null;
  pricePerHour: number;
  rating: number;
  reviewCount: number;
}

export interface FacilityDetailDto extends FacilityDto {
  operatingHours: string;
  amenities: string[];
  latitude: number;
  longitude: number;
}

export interface FacilitySlotDto {
  id: number;
  facilityId: number;
  startAt: string;
  endAt: string;
  available: boolean;
}

export interface FacilitySlotParams {
  date: string; // ISO date string (YYYY-MM-DD)
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function getFacilities(
  params?: FacilityListParams
): Promise<PageResponse<FacilityDto>> {
  const response = await getBeClient().get<PageResponse<FacilityDto>>(PATHS.facilities, {
    params,
  });
  return response.data;
}

export async function getFacilityById(id: number): Promise<FacilityDetailDto> {
  const response = await getBeClient().get<FacilityDetailDto>(PATHS.facilityById(id));
  return response.data;
}

export async function getFacilitySlots(
  id: number,
  params: FacilitySlotParams
): Promise<FacilitySlotDto[]> {
  const response = await getBeClient().get<FacilitySlotDto[]>(PATHS.facilitySlots(id), {
    params,
  });
  return response.data;
}

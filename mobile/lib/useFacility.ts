/**
 * useFacilities — GET /facilities TanStack Query 훅
 * useFacilityDetail — GET /facilities/{id} TanStack Query 훅
 */
import { useQuery } from '@tanstack/react-query';
import { searchFacilities, getFacility } from '../api/facility';
import type { FacilityPageResponse, FacilityResponse, FacilityType } from '../api/types';

interface UseFacilitiesParams {
  gu?: string;
  type?: FacilityType;
}

export function useFacilities({ gu, type }: UseFacilitiesParams) {
  return useQuery<FacilityPageResponse, Error>({
    queryKey: ['facilities', gu, type],
    queryFn: () => searchFacilities({ gu: gu || undefined, type, page: 0, size: 50 }),
    enabled: (gu !== undefined && gu.length > 0) || type !== undefined,
  });
}

export function useFacilityDetail(id: string) {
  return useQuery<FacilityResponse, Error>({
    queryKey: ['facilities', id],
    queryFn: () => getFacility(id),
    enabled: id.length > 0,
  });
}

/**
 * useFacilities.ts — 시설 도메인 react-query 훅
 */
import { useQuery, type UseQueryOptions } from '@tanstack/react-query';
import {
  getFacilities,
  getFacilityById,
  getFacilitySlots,
  type FacilityListParams,
  type FacilityDto,
  type FacilityDetailDto,
  type FacilitySlotDto,
  type PageResponse,
} from '../facilities';
import { facilitiesKeys } from '../queryKeys';

export function useFacilitiesQuery(
  params?: FacilityListParams,
  options?: Omit<UseQueryOptions<PageResponse<FacilityDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: facilitiesKeys.list(params ?? {}),
    queryFn: () => getFacilities(params),
    ...options,
  });
}

export function useFacilityDetailQuery(
  id: number,
  options?: Omit<UseQueryOptions<FacilityDetailDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: facilitiesKeys.detail(id),
    queryFn: () => getFacilityById(id),
    enabled: id > 0,
    ...options,
  });
}

export function useFacilitySlotsQuery(
  facilityId: number,
  date: string,
  options?: Omit<UseQueryOptions<FacilitySlotDto[]>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: facilitiesKeys.slots(facilityId, date),
    queryFn: () => getFacilitySlots(facilityId, { date }),
    enabled: facilityId > 0 && date.length > 0,
    ...options,
  });
}

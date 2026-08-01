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

/** 시설 선택기 한 페이지에 담는 최대 시설 수 — BE `GET /facilities` 기본 size와 맞춘다. */
const FACILITY_OPTION_PAGE_SIZE = 50;

/**
 * 시설 선택기(picker)용 시설 목록. `useFacilities`와 달리 필터 없이도 조회한다 —
 * 사용자가 시설 ID를 직접 입력하지 않고 목록에서 고를 수 있어야 하기 때문이다.
 * BE에 키워드 검색 API가 없어 이름 검색은 호출부에서 클라이언트 필터로 처리한다.
 */
export function useFacilityOptions() {
  return useQuery<FacilityResponse[], Error>({
    queryKey: ['facilities', 'options'],
    queryFn: async () => {
      const page = await searchFacilities({ page: 0, size: FACILITY_OPTION_PAGE_SIZE });
      return page.content;
    },
  });
}

export function useFacilityDetail(id: string) {
  return useQuery<FacilityResponse, Error>({
    queryKey: ['facilities', id],
    queryFn: () => getFacility(id),
    enabled: id.length > 0,
  });
}

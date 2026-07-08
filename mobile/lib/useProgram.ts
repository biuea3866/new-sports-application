/**
 * usePrograms — GET /facilities/{facilityId}/programs TanStack Query 훅
 *
 * 등록·수정은 웹 운영 포털 전용 — 앱은 조회만 다룬다(design-fe-app "기능별 담당 플랫폼").
 */
import { useQuery } from '@tanstack/react-query';

import { listPrograms } from '../api/program';
import type { ProgramResponse } from '../api/program';

export function programsQueryKey(facilityId: string) {
  return ['facilities', facilityId, 'programs'] as const;
}

export function usePrograms(facilityId: string) {
  return useQuery<ProgramResponse[], Error>({
    queryKey: programsQueryKey(facilityId),
    queryFn: () => listPrograms(facilityId),
    enabled: facilityId.length > 0,
  });
}

/**
 * useLimitedDrop — GET /limited-drops/{dropId} TanStack Query 훅
 *
 * 근거: design-fe-app.md "상태관리 설계" · "API 연동 표"
 * 회차 정보(status·remaining·openAt)는 서버 상태 — Query 캐시가 SSOT이며 스토어에 복사하지 않는다.
 * status가 OPEN이거나 openAt이 1분 이내로 임박하면 refetchInterval로 remaining을 동기화한다.
 */
import { useQuery } from '@tanstack/react-query';

import { getLimitedDrop } from '../api/limitedDrops';
import type { LimitedDropResponse } from '../api/types';

const NEAR_OPEN_THRESHOLD_MS = 60 * 1000;
const POLL_INTERVAL_MS = 3 * 1000;

/**
 * 회차 데이터로부터 폴링 간격을 결정한다.
 * OPEN 상태이거나 openAt이 임박(1분 이내)했으면 폴링 간격을, 그 외에는 false(폴링 없음)를 반환한다.
 */
export function getRefetchIntervalMs(drop: LimitedDropResponse | undefined): number | false {
  if (!drop) {
    return false;
  }
  if (drop.status === 'OPEN') {
    return POLL_INTERVAL_MS;
  }

  const msUntilOpen = new Date(drop.openAt).getTime() - Date.now();
  const isNearOpen = msUntilOpen > 0 && msUntilOpen <= NEAR_OPEN_THRESHOLD_MS;
  return isNearOpen ? POLL_INTERVAL_MS : false;
}

export function useLimitedDrop(dropId: number) {
  return useQuery<LimitedDropResponse, Error>({
    queryKey: ['limitedDrops', dropId],
    queryFn: () => getLimitedDrop(dropId),
    refetchInterval: (query) => getRefetchIntervalMs(query.state.data),
  });
}

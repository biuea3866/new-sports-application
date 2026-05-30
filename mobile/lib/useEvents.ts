/**
 * useEvents — GET /events TanStack Query 훅
 * status 필터 변경 시 자동으로 재조회됩니다.
 */
import { useQuery } from '@tanstack/react-query';
import { listEvents } from '../api/event';
import type { EventStatus, ListEventsResponse } from '../api/types';

export const EVENTS_QUERY_KEY = ['events'] as const;

export function useEvents(page = 0, size = 20, status?: EventStatus) {
  return useQuery<ListEventsResponse, Error>({
    queryKey: [...EVENTS_QUERY_KEY, page, size, status],
    queryFn: () => listEvents(page, size, status),
  });
}

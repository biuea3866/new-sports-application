/**
 * useEvent — GET /events/{id} TanStack Query 훅
 */
import { useQuery } from '@tanstack/react-query';
import { getEvent } from '../api/event';
import type { EventDetailResponse } from '../api/types';

export function useEvent(id: number) {
  return useQuery<EventDetailResponse, Error>({
    queryKey: ['events', id],
    queryFn: () => getEvent(id),
    enabled: id > 0,
  });
}

/**
 * useEvents.ts — 이벤트 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  getEvents,
  getEventById,
  selectSeats,
  releaseSeats,
  type EventListParams,
  type EventDto,
  type EventDetailDto,
  type SelectSeatsRequest,
  type SelectedSeatsDto,
  type ReleaseSeatsRequest,
} from '../events';
import { type PageResponse } from '../facilities';
import { eventsKeys } from '../queryKeys';

export function useEventsQuery(
  params?: EventListParams,
  options?: Omit<UseQueryOptions<PageResponse<EventDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: eventsKeys.list(params ?? {}),
    queryFn: () => getEvents(params),
    ...options,
  });
}

export function useEventDetailQuery(
  id: number,
  options?: Omit<UseQueryOptions<EventDetailDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: eventsKeys.detail(id),
    queryFn: () => getEventById(id),
    enabled: id > 0,
    ...options,
  });
}

export function useSelectSeatsMutation(
  eventId: number,
  options?: UseMutationOptions<SelectedSeatsDto, Error, SelectSeatsRequest>
) {
  return useMutation({
    mutationFn: (request: SelectSeatsRequest) => selectSeats(eventId, request),
    ...options,
  });
}

export function useReleaseSeatsMutation(
  eventId: number,
  options?: UseMutationOptions<void, Error, ReleaseSeatsRequest>
) {
  return useMutation({
    mutationFn: (request: ReleaseSeatsRequest) => releaseSeats(eventId, request),
    ...options,
  });
}

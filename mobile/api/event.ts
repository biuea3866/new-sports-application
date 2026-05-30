/**
 * event.ts — 이벤트 API 함수
 */
import { getBeClient } from './be-client';
import type {
  EventDetailResponse,
  EventStatus,
  ListEventsResponse,
} from './types';

export async function listEvents(
  page = 0,
  size = 20,
  status?: EventStatus
): Promise<ListEventsResponse> {
  const res = await getBeClient().get<ListEventsResponse>('/events', {
    params: { page, size, ...(status ? { status } : {}) },
  });
  return res.data;
}

export async function getEvent(id: number): Promise<EventDetailResponse> {
  const res = await getBeClient().get<EventDetailResponse>(`/events/${id}`);
  return res.data;
}

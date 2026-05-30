/**
 * events.ts — 이벤트 도메인 API 함수
 *
 * BE 경로:
 *   GET  /events                       — 이벤트 목록
 *   GET  /events/{id}                  — 이벤트 상세
 *   POST /events/{id}/seats/select     — 좌석 선점
 *   POST /events/{id}/seats/release    — 좌석 해제
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export interface EventListParams {
  keyword?: string;
  category?: string;
  startFrom?: string;
  page?: number;
  size?: number;
}

export interface SeatDto {
  id: number;
  seatNumber: string;
  grade: string;
  price: number;
  available: boolean;
}

export interface EventDto {
  id: number;
  title: string;
  category: string;
  venue: string;
  startAt: string;
  endAt: string;
  imageUrl: string | null;
  minPrice: number;
  maxPrice: number;
  remainingSeats: number;
}

export interface EventDetailDto extends EventDto {
  description: string;
  seats: SeatDto[];
}

export interface SelectSeatsRequest {
  seatIds: number[];
}

export interface SelectedSeatsDto {
  seats: SeatDto[];
  reservationToken: string;
  expiresAt: string;
}

export interface ReleaseSeatsRequest {
  reservationToken: string;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function getEvents(params?: EventListParams): Promise<PageResponse<EventDto>> {
  const response = await getBeClient().get<PageResponse<EventDto>>(PATHS.events, { params });
  return response.data;
}

export async function getEventById(id: number): Promise<EventDetailDto> {
  const response = await getBeClient().get<EventDetailDto>(PATHS.eventById(id));
  return response.data;
}

export async function selectSeats(
  id: number,
  request: SelectSeatsRequest
): Promise<SelectedSeatsDto> {
  const response = await getBeClient().post<SelectedSeatsDto>(PATHS.eventSeatSelect(id), request);
  return response.data;
}

export async function releaseSeats(id: number, request: ReleaseSeatsRequest): Promise<void> {
  await getBeClient().post(PATHS.eventSeatRelease(id), request);
}

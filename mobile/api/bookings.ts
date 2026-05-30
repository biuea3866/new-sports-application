/**
 * bookings.ts — 예약 도메인 API 함수
 *
 * BE 경로:
 *   POST /bookings              — 예약 생성
 *   GET  /bookings/me          — 내 예약 목록
 *   GET  /bookings/{id}        — 예약 상세
 *   POST /bookings/{id}/cancel — 예약 취소
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';

export interface CreateBookingRequest {
  facilityId: number;
  slotId: number;
}

export interface BookingDto {
  id: number;
  facilityId: number;
  facilityName: string;
  slotId: number;
  startAt: string;
  endAt: string;
  status: BookingStatus;
  totalPrice: number;
  createdAt: string;
}

export interface BookingListParams {
  status?: BookingStatus;
  page?: number;
  size?: number;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function createBooking(request: CreateBookingRequest): Promise<BookingDto> {
  const response = await getBeClient().post<BookingDto>(PATHS.bookings, request);
  return response.data;
}

export async function getMyBookings(params?: BookingListParams): Promise<PageResponse<BookingDto>> {
  const response = await getBeClient().get<PageResponse<BookingDto>>(PATHS.bookingsMe, { params });
  return response.data;
}

export async function getBookingById(id: number): Promise<BookingDto> {
  const response = await getBeClient().get<BookingDto>(PATHS.bookingById(id));
  return response.data;
}

export async function cancelBooking(id: number): Promise<BookingDto> {
  const response = await getBeClient().post<BookingDto>(PATHS.bookingCancel(id));
  return response.data;
}

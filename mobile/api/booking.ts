/**
 * booking.ts — 예약 API 함수
 */
import { getBeClient } from './be-client';
import type { BookingResponse, CancelBookingRequest, ListBookingsResponse } from './types';

export async function listMyBookings(page = 0, size = 20): Promise<ListBookingsResponse> {
  const res = await getBeClient().get<ListBookingsResponse>('/bookings/me', {
    params: { page, size },
  });
  return res.data;
}

export async function cancelBooking(
  id: number,
  body: CancelBookingRequest = {}
): Promise<BookingResponse> {
  const res = await getBeClient().post<BookingResponse>(`/bookings/${id}/cancel`, body);
  return res.data;
}

/**
 * booking.ts — 예약 API 함수
 */
import { getBeClient } from './be-client';
import type {
  BookingResponse,
  CancelBookingRequest,
  CreateBookingBody,
  CreateBookingResult,
  ListBookingsResponse,
  SlotResponse,
} from './types';

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

export async function listSlots(facilityId: string): Promise<SlotResponse[]> {
  const res = await getBeClient().get<SlotResponse[]>(`/facilities/${facilityId}/slots`);
  return res.data;
}

export async function createBooking(body: CreateBookingBody): Promise<CreateBookingResult> {
  const res = await getBeClient().post<CreateBookingResult>('/bookings', body);
  return res.data;
}

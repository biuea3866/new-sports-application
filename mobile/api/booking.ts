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

/** `GET /bookings/{id}` — 예약 상세(단건). 주문상세(Option A) 화면이 사용한다. */
export async function getBookingDetail(id: number): Promise<BookingResponse> {
  const res = await getBeClient().get<BookingResponse>(`/bookings/${id}`);
  return res.data;
}

export async function cancelBooking(
  id: number,
  body: CancelBookingRequest = {}
): Promise<BookingResponse> {
  const res = await getBeClient().post<BookingResponse>(`/bookings/${id}/cancel`, body);
  return res.data;
}

/** `GET /facilities/{facilityId}/slots?programId=` — programId 지정 시 해당 program 회차만 필터. */
export async function listSlots(facilityId: string, programId?: number): Promise<SlotResponse[]> {
  const res = await getBeClient().get<SlotResponse[]>(`/facilities/${facilityId}/slots`, {
    params: programId !== undefined ? { programId } : undefined,
  });
  return res.data;
}

export async function createBooking(body: CreateBookingBody): Promise<CreateBookingResult> {
  const res = await getBeClient().post<CreateBookingResult>('/bookings', body);
  return res.data;
}

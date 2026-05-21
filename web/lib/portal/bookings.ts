/**
 * 예약 관련 타입 정의 및 BFF API 클라이언트.
 * Client Component에서는 /api/portal/bookings BFF 엔드포인트만 호출한다.
 */

export type BookingStatus = "PENDING" | "CONFIRMED" | "CANCELLED" | "EXPIRED";

export interface BookingResponse {
  id: number;
  slotId: number;
  userId: number;
  status: BookingStatus;
  paymentId: number | null;
  paymentStatus: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ListBookingsResponse {
  bookings: BookingResponse[];
  totalElements: number;
  totalPages: number;
  page: number;
  size: number;
}

export interface ListBookingsParams {
  status?: BookingStatus;
  page?: number;
  size?: number;
}

/** 내 예약 목록 조회 */
export async function fetchMyBookings(params: ListBookingsParams = {}): Promise<ListBookingsResponse> {
  const query = new URLSearchParams();
  if (params.status !== undefined) query.set("status", params.status);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const qs = query.toString();
  const url = qs ? `/api/portal/bookings?${qs}` : "/api/portal/bookings";

  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `예약 목록 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<ListBookingsResponse>;
}

/** 예약 단건 조회 */
export async function fetchBooking(bookingId: number): Promise<BookingResponse> {
  const res = await fetch(`/api/portal/bookings/${bookingId}`, { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `예약 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<BookingResponse>;
}

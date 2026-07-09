/**
 * ticketOrders.ts — 티켓 주문 API 함수
 *
 * 모든 호출은 getBeClient()를 통해 backend WAS를 경유합니다.
 * 외부 API 직접 호출 금지 (fe-external-api-via-was.md).
 */
import { getBeClient } from './be-client';
import type {
  PurchaseTicketOrderRequest,
  SelectSeatsResponse,
  TicketOrderDetailResponse,
  TicketOrderResponse,
} from './types';

function generateIdempotencyKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0;
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

/**
 * 좌석 선점 요청
 * POST /events/{eventId}/seats/select
 * 성공 시 lockId와 expiresAt 반환
 */
export async function selectSeats(
  eventId: number,
  seatIds: number[]
): Promise<SelectSeatsResponse> {
  const res = await getBeClient().post<SelectSeatsResponse>(
    `/events/${eventId}/seats/select`,
    { seatIds }
  );
  return res.data;
}

/**
 * 좌석 선점 해제
 * POST /events/{eventId}/seats/release
 */
export async function releaseSeats(eventId: number, seatIds: number[]): Promise<void> {
  await getBeClient().post(`/events/${eventId}/seats/release`, { seatIds });
}

/**
 * 티켓 주문 생성
 * POST /ticket-orders
 * idempotencyKey 미전달 시 자동 생성
 */
export async function purchaseTicketOrder(
  body: PurchaseTicketOrderRequest,
  idempotencyKey?: string
): Promise<TicketOrderResponse> {
  const key = idempotencyKey ?? generateIdempotencyKey();
  const res = await getBeClient().post<TicketOrderResponse>('/ticket-orders', body, {
    headers: { 'Idempotency-Key': key },
  });
  return res.data;
}

/**
 * 티켓 주문 상세 조회
 * GET /ticket-orders/{id}
 * 주문상세(Option A+) 화면이 사용한다. 응답은 `TicketOrderDetailResponse`
 * (ticketOrderId·status·eventId·eventTitle·paymentId·createdAt) —
 * 구매 응답(`TicketOrderResponse`)과는 별도 DTO다(BE `feat/ticket-order-detail-enrich`).
 */
export async function getTicketOrderDetail(id: number): Promise<TicketOrderDetailResponse> {
  const res = await getBeClient().get<TicketOrderDetailResponse>(`/ticket-orders/${id}`);
  return res.data;
}

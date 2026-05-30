/**
 * ticketOrders.ts — 티켓 주문 도메인 API 함수
 *
 * BE 경로:
 *   POST /ticket-orders — 티켓 주문 생성
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export type TicketOrderStatus =
  | 'PENDING'
  | 'PAYMENT_WAITING'
  | 'PAID'
  | 'CANCELLED'
  | 'REFUNDED';

export interface CreateTicketOrderRequest {
  eventId: number;
  seatIds: number[];
  reservationToken: string;
}

export interface TicketOrderItemDto {
  seatId: number;
  seatNumber: string;
  grade: string;
  price: number;
}

export interface TicketOrderDto {
  id: number;
  orderNumber: string;
  eventId: number;
  eventTitle: string;
  status: TicketOrderStatus;
  items: TicketOrderItemDto[];
  totalPrice: number;
  createdAt: string;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function createTicketOrder(
  request: CreateTicketOrderRequest
): Promise<TicketOrderDto> {
  const response = await getBeClient().post<TicketOrderDto>(PATHS.ticketOrders, request);
  return response.data;
}

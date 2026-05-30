/**
 * payments.ts — 결제 도메인 API 함수
 *
 * BE 경로:
 *   POST /payments       — 결제 실행
 *   GET  /payments/me    — 내 결제 목록
 *   GET  /payments/{id}  — 결제 상세
 *
 * 참고: 결제 준비(prepare) / webhook 처리는 BE-07에서 별도 구현.
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'REFUNDED';

export type PaymentOrderType = 'BOOKING' | 'GOODS_ORDER' | 'TICKET_ORDER';

export interface ExecutePaymentRequest {
  orderId: number;
  orderType: PaymentOrderType;
  paymentMethodKey: string;
  amount: number;
}

export interface PaymentDto {
  id: number;
  orderId: number;
  orderType: PaymentOrderType;
  amount: number;
  status: PaymentStatus;
  paymentMethodKey: string;
  paidAt: string | null;
  createdAt: string;
}

export interface PaymentListParams {
  status?: PaymentStatus;
  page?: number;
  size?: number;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function executePayment(request: ExecutePaymentRequest): Promise<PaymentDto> {
  const response = await getBeClient().post<PaymentDto>(PATHS.payments, request);
  return response.data;
}

export async function getMyPayments(params?: PaymentListParams): Promise<PageResponse<PaymentDto>> {
  const response = await getBeClient().get<PageResponse<PaymentDto>>(PATHS.paymentsMe, { params });
  return response.data;
}

export async function getPaymentById(id: number): Promise<PaymentDto> {
  const response = await getBeClient().get<PaymentDto>(PATHS.paymentById(id));
  return response.data;
}

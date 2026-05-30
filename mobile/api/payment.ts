/**
 * payment.ts — 결제 API 타입 및 호출
 *
 * BE CreatePaymentRequest / PaymentResponse shape 매칭.
 * OrderType: BOOKING | TICKETING | GOODS
 * PaymentMethod: KAKAO | TOSS | NAVER | DANAL | CREDIT_CARD | BANK_TRANSFER
 * PaymentStatus: PENDING | COMPLETED | FAILED | REFUNDED
 */
import { getBeClient } from './be-client';
import type { PaymentHistoryListResponse } from './types';

export type OrderType = 'BOOKING' | 'TICKETING' | 'GOODS';

export type PaymentMethod = 'KAKAO' | 'TOSS' | 'NAVER' | 'DANAL' | 'CREDIT_CARD' | 'BANK_TRANSFER';

export type PaymentStatus = 'PENDING' | 'COMPLETED' | 'FAILED' | 'REFUNDED';

export interface CreatePaymentBody {
  orderType: OrderType;
  orderId: number;
  method: PaymentMethod;
  amount: number;
  currency: 'KRW';
}

export interface PaymentResponse {
  id: number;
  orderType: OrderType;
  orderId: number;
  method: PaymentMethod;
  amount: number;
  status: PaymentStatus;
  createdAt: string;
  paidAt: string | null;
}

export async function createPayment(
  body: CreatePaymentBody,
  idempotencyKey: string
): Promise<PaymentResponse> {
  const client = getBeClient();
  const response = await client.post<PaymentResponse>('/payments', body, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return response.data;
}

export async function getMyPayments(
  page = 0,
  size = 20,
  status?: PaymentStatus
): Promise<PaymentHistoryListResponse> {
  const client = getBeClient();
  const params: Record<string, string | number> = { page, size };
  if (status !== undefined) {
    params.status = status;
  }
  const response = await client.get<PaymentHistoryListResponse>('/payments/me', { params });
  return response.data;
}

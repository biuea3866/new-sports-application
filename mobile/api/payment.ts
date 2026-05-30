/**
 * payment.ts — 결제 API 타입 및 호출
 *
 * BE CreatePaymentRequest / PaymentResponse shape 매칭.
 * OrderType: BOOKING | TICKETING | GOODS
 * PaymentMethod: KAKAO | TOSS | NAVER | DANAL | CREDIT_CARD | BANK_TRANSFER | MOBILE_PAY
 * PaymentStatus: PENDING | READY | COMPLETED | CANCELLED | FAILED | REFUNDED
 */
import { getBeClient } from './be-client';

export type OrderType = 'BOOKING' | 'TICKETING' | 'GOODS';

export type PaymentMethod =
  | 'KAKAO'
  | 'TOSS'
  | 'NAVER'
  | 'DANAL'
  | 'CREDIT_CARD'
  | 'BANK_TRANSFER'
  | 'MOBILE_PAY';

export type PaymentStatus =
  | 'PENDING'
  | 'READY'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED'
  | 'REFUNDED';

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

/** POST /payments/prepare — 결제창 URL 발급 */
export interface PreparePaymentBody {
  orderType: OrderType;
  orderId: number;
  method: PaymentMethod;
  amount: number;
  currency: 'KRW';
  itemName: string;
  returnUrl: string;
  failUrl: string;
}

export interface PreparePaymentResponse {
  paymentId: number;
  checkoutUrl: string;
  pgTransactionId: string;
}

/** GET /payments/{id} — 결제 상세 */
export interface PaymentDetailResponse {
  id: number;
  orderType: OrderType;
  orderId: number;
  method: PaymentMethod;
  amount: number;
  currency: string;
  status: PaymentStatus;
  itemName: string;
  pgTransactionId: string | null;
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

export async function preparePayment(
  body: PreparePaymentBody,
  idempotencyKey: string
): Promise<PreparePaymentResponse> {
  const client = getBeClient();
  const response = await client.post<PreparePaymentResponse>('/payments/prepare', body, {
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return response.data;
}

export async function getPayment(id: number): Promise<PaymentDetailResponse> {
  const client = getBeClient();
  const response = await client.get<PaymentDetailResponse>(`/payments/${id}`);
  return response.data;
}

/**
 * payment.ts — 결제 API 타입 및 호출
 *
 * BE CreatePaymentRequest / PaymentResponse shape 매칭.
 * OrderType: BOOKING | TICKETING | GOODS | RECRUITMENT
 * PaymentMethod: KAKAO | TOSS | NAVER | DANAL | CREDIT_CARD | BANK_TRANSFER | MOBILE_PAY | VIRTUAL_ACCOUNT
 * PaymentStatus: PENDING | READY | COMPLETED | CANCELLED | FAILED | REFUNDED
 */
import { getBeClient } from './be-client';

/**
 * BE `domain/payment/vo/OrderType`. `RECRUITMENT`는 모집 신청 결제(BE-53, design-fe-app
 * "결제 흐름 재사용 결정")를 위한 additive 확장 — 화면 배선은 후속 wave.
 */
export type OrderType = 'BOOKING' | 'TICKETING' | 'GOODS' | 'RECRUITMENT';

/** BE `domain/payment/vo/PaymentMethod`(8종) 전수 매칭. */
export type PaymentMethod =
  | 'KAKAO'
  | 'TOSS'
  | 'NAVER'
  | 'DANAL'
  | 'CREDIT_CARD'
  | 'BANK_TRANSFER'
  | 'MOBILE_PAY'
  | 'VIRTUAL_ACCOUNT';

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

/**
 * "pre-issued" 결제 진입 파라미터 — 서버가 신청과 동시에 결제를 이미 prepare한 상태
 * (`POST /recruitments/{id}/applications` 응답의 `paymentId`/`checkoutUrl`)로 결제 화면에
 * 진입할 때 쓰는 라우트 파라미터 타입. 이 모드에서는 `preparePayment` 호출을 건너뛰고
 * 바로 `checkoutUrl`을 연 뒤 `getPayment(paymentId)` 폴링으로 이어간다
 * (design-fe-app "결제 흐름 재사용 결정"). 화면(`app/payment/new.tsx`) 배선은 후속 wave.
 */
export interface PreIssuedPaymentParams {
  orderType: OrderType;
  orderId: number;
  paymentId: number;
  checkoutUrl: string;
}

/**
 * expo-router의 `useLocalSearchParams()` 반환값(문자열 또는 문자열 배열)에서
 * pre-issued 모드 진입 여부를 판별한다. `paymentId`·`checkoutUrl` 파라미터가 둘 다
 * 단일 문자열로 존재해야 pre-issued로 인정한다.
 */
export function isPreIssuedPaymentParams(params: {
  paymentId?: string | string[];
  checkoutUrl?: string | string[];
}): boolean {
  return typeof params.paymentId === 'string' && typeof params.checkoutUrl === 'string';
}

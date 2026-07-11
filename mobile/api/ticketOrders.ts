/**
 * ticketOrders.ts — 티켓 주문 API 함수
 *
 * 모든 호출은 getBeClient()를 통해 backend WAS를 경유합니다.
 * 외부 API 직접 호출 금지 (fe-external-api-via-was.md).
 *
 * 가상 대기열(FE-09, `20260709-가상대기열-design-fe-app.md` "API 연동 표"): 좌석 선점
 * `selectSeats`는 `entryTokenStore`(FE-02)에 저장된 입장 토큰이 있으면 `X-Entry-Token`
 * 헤더로 부착한다. 경로·body는 불변 — 헤더만 추가. `be-client.ts`가 `useAuthStore.getState()`를
 * React 밖(인터셉터)에서 쓰는 선례와 동형으로, 이 함수도 `useEntryTokenStore.getState()`를 쓴다.
 * 플래그 OFF·토큰 미저장이면 기존과 동일하게 헤더 없이 호출된다.
 */
import { AxiosError } from 'axios';

import { getBeClient } from './be-client';
import { useEntryTokenStore } from '../lib/entryTokenStore';
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
 *
 * 저장된 입장 토큰(대기실 통과 시 `entryTokenStore`에 저장됨)이 있으면 `X-Entry-Token`
 * 헤더로 부착한다. 토큰이 없으면(플래그 OFF·대기실 미경유) 기존과 동일하게 헤더 없이 호출한다.
 */
export async function selectSeats(
  eventId: number,
  seatIds: number[]
): Promise<SelectSeatsResponse> {
  const entryToken = useEntryTokenStore.getState().tokenFor('ticketing-event', eventId);
  const res = await getBeClient().post<SelectSeatsResponse>(
    `/events/${eventId}/seats/select`,
    { seatIds },
    entryToken ? { headers: { 'X-Entry-Token': entryToken } } : undefined
  );
  return res.data;
}

/**
 * 좌석 선점 API가 가상 대기열 우회로 거부됐는지 판별한다.
 * BE 계약(TDD "FE/외부 계약"): 403 `{ "code": "QUEUE_BYPASS_DENIED" }` — 토큰 부재·위조·만료.
 * `order.tsx`가 이 판별로 일반 오류와 구분해 "다시 대기하기" 안내를 띄운다.
 */
export function isQueueBypassDeniedError(error: unknown): boolean {
  if (!(error instanceof AxiosError) || !error.response) {
    return false;
  }
  const body = error.response.data as { code?: string } | undefined;
  return error.response.status === 403 && body?.code === 'QUEUE_BYPASS_DENIED';
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

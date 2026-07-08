/**
 * orderHistory.ts — 내 주문 통합 조회 API 함수
 *
 * BE 계약: GET /api/orders (authenticated) → OrderHistoryResponse
 * 인증 헤더는 be-client 인터셉터가 자동 부착한다.
 */
import { getBeClient } from './be-client';
import type { OrderHistoryCriteria, OrderHistoryResponse } from './order-history-types';

export async function getOrderHistory(
  criteria: OrderHistoryCriteria
): Promise<OrderHistoryResponse> {
  const { orderType, status, page, size } = criteria;
  const res = await getBeClient().get<OrderHistoryResponse>('/api/orders', {
    params: {
      ...(orderType !== undefined ? { orderType } : {}),
      ...(status !== undefined ? { status } : {}),
      page,
      size,
    },
  });
  return res.data;
}

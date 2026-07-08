/**
 * useOrderHistory — GET /api/orders (authenticated) TanStack Query 훅
 *
 * 내 주문 통합 조회 화면이 소비하는 서버 상태 훅. 서버 데이터는 Query 캐시가
 * SSOT이며 스토어에 복사하지 않는다(design-fe-app "상태관리 설계").
 *
 * 401은 be-client 인터셉터가 refresh를 선처리하고, 그래도 실패하면 이 훅은
 * isError로 그대로 노출한다 — 로그인 유도 UI는 화면(OrderHistoryScreen) 책임.
 */
import { useQuery } from '@tanstack/react-query';

import { getOrderHistory } from '../api/orderHistory';
import type { OrderHistoryCriteria, OrderHistoryResponse } from '../api/order-history-types';

export const ORDER_HISTORY_QUERY_KEY = ['orderHistory'] as const;

export function orderHistoryQueryKey(criteria: Partial<OrderHistoryCriteria>) {
  return [
    ...ORDER_HISTORY_QUERY_KEY,
    criteria.orderType ?? null,
    criteria.status ?? null,
    criteria.page,
    criteria.size,
  ] as const;
}

/** `GET /api/orders?orderType=&status=&page=&size=` — orderType·status는 선택 필터. */
export function useOrderHistory(criteria: Partial<OrderHistoryCriteria>) {
  const { page = 0, size = 20 } = criteria;
  const resolvedCriteria: OrderHistoryCriteria = { ...criteria, page, size };

  return useQuery<OrderHistoryResponse, Error>({
    queryKey: orderHistoryQueryKey(resolvedCriteria),
    queryFn: () => getOrderHistory(resolvedCriteria),
  });
}

/**
 * useOrderDetail — 통합 주문내역 항목 탭 → 주문 상세(Option A+, `app/orders/[orderType]/[id].tsx`)
 * 화면이 쓰는 TanStack Query 훅.
 *
 * orderType별로 서로 다른 BE 엔드포인트/응답 타입을 호출해야 하므로(booking/goods-orders/
 * ticket-orders/applications), 판별 유니온(`OrderDetailQueryResult`)으로 결과를 감싼다.
 * `data.orderType`으로 좁히면 `data.data`가 해당 orderType의 실제 응답 타입으로 좁혀진다
 * (`as` 타입 단언 없이 switch 분기의 리터럴 반환값만으로 유니온을 구성).
 *
 * 서버 데이터는 Query 캐시가 SSOT — 지역 상태에 복사하지 않는다.
 */
import { useQuery } from '@tanstack/react-query';

import { getBookingDetail } from '../api/booking';
import { getGoodsOrderDetail } from '../api/goodsOrders';
import { getTicketOrderDetail } from '../api/ticketOrders';
import { getApplicationDetail } from '../api/recruitment';
import type { ApplicationDetailResponse } from '../api/recruitment';
import type {
  BookingResponse,
  GoodsOrderDetailResponse,
  TicketOrderDetailResponse,
} from '../api/types';
import type { OrderType } from '../api/order-history-types';

export type OrderDetailQueryResult =
  | { orderType: 'BOOKING'; data: BookingResponse }
  | { orderType: 'GOODS'; data: GoodsOrderDetailResponse }
  | { orderType: 'TICKETING'; data: TicketOrderDetailResponse }
  | { orderType: 'RECRUITMENT'; data: ApplicationDetailResponse };

export function orderDetailQueryKey(orderType: OrderType, id: number) {
  return ['orderDetail', orderType, id] as const;
}

async function fetchOrderDetail(orderType: OrderType, id: number): Promise<OrderDetailQueryResult> {
  switch (orderType) {
    case 'BOOKING':
      return { orderType, data: await getBookingDetail(id) };
    case 'GOODS':
      return { orderType, data: await getGoodsOrderDetail(id) };
    case 'TICKETING':
      return { orderType, data: await getTicketOrderDetail(id) };
    case 'RECRUITMENT':
      return { orderType, data: await getApplicationDetail(id) };
  }
}

/** `id`가 양수일 때만 조회한다(라우트 파라미터 파싱 실패 등 방어). */
export function useOrderDetail(orderType: OrderType, id: number) {
  return useQuery<OrderDetailQueryResult, Error>({
    queryKey: orderDetailQueryKey(orderType, id),
    queryFn: () => fetchOrderDetail(orderType, id),
    enabled: id > 0,
  });
}

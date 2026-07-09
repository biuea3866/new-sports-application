/**
 * useGoodsOrders — 물품 주문 TanStack Query 훅 모음
 *
 * - useMyGoodsOrdersQuery: GET /goods-orders/me
 * - useGoodsOrderDetailQuery: GET /goods-orders/{id}
 * - useCreateGoodsOrderMutation: POST /goods-orders
 */
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { createGoodsOrder, getGoodsOrderDetail, getMyGoodsOrders } from '../api/goodsOrders';
import type {
  CreateGoodsOrderRequest,
  GoodsOrderDetailResponse,
  GoodsOrderListResponse,
  GoodsOrderResponse,
} from '../api/types';

export const GOODS_ORDERS_QUERY_KEY = ['goodsOrders'] as const;

export function useMyGoodsOrdersQuery(page = 0, size = 20) {
  return useQuery<GoodsOrderListResponse, Error>({
    queryKey: [...GOODS_ORDERS_QUERY_KEY, 'me', page, size],
    queryFn: () => getMyGoodsOrders(page, size),
  });
}

export function useGoodsOrderDetailQuery(id: number) {
  return useQuery<GoodsOrderDetailResponse, Error>({
    queryKey: [...GOODS_ORDERS_QUERY_KEY, id],
    queryFn: () => getGoodsOrderDetail(id),
    enabled: id > 0,
  });
}

export function useCreateGoodsOrderMutation() {
  const queryClient = useQueryClient();

  return useMutation<GoodsOrderResponse, Error, CreateGoodsOrderRequest>({
    mutationFn: createGoodsOrder,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: GOODS_ORDERS_QUERY_KEY });
    },
  });
}

/**
 * useGoodsOrders.ts — 상품 주문 도메인 react-query 훅
 */
import {
  useQuery,
  useMutation,
  useQueryClient,
  type UseQueryOptions,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {
  createGoodsOrder,
  getGoodsOrderById,
  getMyGoodsOrders,
  type CreateGoodsOrderRequest,
  type GoodsOrderDto,
  type GoodsOrderListParams,
} from '../goodsOrders';
import { type PageResponse } from '../facilities';
import { goodsOrdersKeys, cartKeys } from '../queryKeys';

export function useMyGoodsOrdersQuery(
  params?: GoodsOrderListParams,
  options?: Omit<UseQueryOptions<PageResponse<GoodsOrderDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: goodsOrdersKeys.myList(params ?? {}),
    queryFn: () => getMyGoodsOrders(params),
    ...options,
  });
}

export function useGoodsOrderDetailQuery(
  id: number,
  options?: Omit<UseQueryOptions<GoodsOrderDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: goodsOrdersKeys.detail(id),
    queryFn: () => getGoodsOrderById(id),
    enabled: id > 0,
    ...options,
  });
}

export function useCreateGoodsOrderMutation(
  options?: UseMutationOptions<GoodsOrderDto, Error, CreateGoodsOrderRequest>
) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createGoodsOrder,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: goodsOrdersKeys.mine() });
      // 주문 생성 시 장바구니도 비워질 수 있으므로 무효화
      queryClient.invalidateQueries({ queryKey: cartKeys.mine() });
    },
    ...options,
  });
}

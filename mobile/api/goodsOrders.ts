/**
 * goodsOrders.ts — 물품 주문 API 함수
 */
import { getBeClient } from './be-client';
import type {
  CreateGoodsOrderRequest,
  GoodsOrderListResponse,
  GoodsOrderResponse,
} from './types';

export async function getMyGoodsOrders(
  page = 0,
  size = 20
): Promise<GoodsOrderListResponse> {
  const res = await getBeClient().get<GoodsOrderListResponse>('/goods-orders/me', {
    params: { page, size },
  });
  return res.data;
}

export async function getGoodsOrderDetail(id: number): Promise<GoodsOrderResponse> {
  const res = await getBeClient().get<GoodsOrderResponse>(`/goods-orders/${id}`);
  return res.data;
}

export async function createGoodsOrder(
  body: CreateGoodsOrderRequest
): Promise<GoodsOrderResponse> {
  const res = await getBeClient().post<GoodsOrderResponse>('/goods-orders', body);
  return res.data;
}

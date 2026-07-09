/**
 * goodsOrders.ts — 물품 주문 API 함수
 */
import { getBeClient } from './be-client';
import type {
  CreateGoodsOrderRequest,
  GoodsOrderDetailResponse,
  GoodsOrderListResponse,
  GoodsOrderResponse,
} from './types';

export async function getMyGoodsOrders(page = 0, size = 20): Promise<GoodsOrderListResponse> {
  const res = await getBeClient().get<GoodsOrderListResponse>('/goods-orders/me', {
    params: { page, size },
  });
  return res.data;
}

/**
 * `GET /goods-orders/{orderId}` — 물품 주문 상세(단건).
 * 응답 타입은 백엔드 실제 계약(`GoodsOrderDetailResponse` — `api/types.ts` 주석 참조)과
 * 일치시킨다. 기존 `GoodsOrderResponse`(주문 목록 화면 전용, createdAt/productName 보유)는
 * 이 엔드포인트의 실제 응답과 달라 사용하지 않는다.
 */
export async function getGoodsOrderDetail(id: number): Promise<GoodsOrderDetailResponse> {
  const res = await getBeClient().get<GoodsOrderDetailResponse>(`/goods-orders/${id}`);
  return res.data;
}

export async function createGoodsOrder(body: CreateGoodsOrderRequest): Promise<GoodsOrderResponse> {
  const res = await getBeClient().post<GoodsOrderResponse>('/goods-orders', body);
  return res.data;
}

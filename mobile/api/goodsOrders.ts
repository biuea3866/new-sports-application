/**
 * goodsOrders.ts — 상품 주문 도메인 API 함수
 *
 * BE 경로:
 *   POST /goods-orders       — 상품 주문 생성
 *   GET  /goods-orders/{id}  — 주문 상세
 *   GET  /goods-orders/me    — 내 주문 목록
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export type GoodsOrderStatus =
  | 'PENDING'
  | 'PAYMENT_WAITING'
  | 'PAID'
  | 'PREPARING'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'REFUNDED';

export interface GoodsOrderItemDto {
  productId: number;
  productName: string;
  quantity: number;
  unitPrice: number;
  totalPrice: number;
}

export interface CreateGoodsOrderRequest {
  items: Array<{
    productId: number;
    quantity: number;
  }>;
  shippingAddress: string;
  receiverName: string;
  receiverPhone: string;
}

export interface GoodsOrderDto {
  id: number;
  orderNumber: string;
  status: GoodsOrderStatus;
  items: GoodsOrderItemDto[];
  totalPrice: number;
  shippingAddress: string;
  receiverName: string;
  receiverPhone: string;
  createdAt: string;
}

export interface GoodsOrderListParams {
  status?: GoodsOrderStatus;
  page?: number;
  size?: number;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function createGoodsOrder(request: CreateGoodsOrderRequest): Promise<GoodsOrderDto> {
  const response = await getBeClient().post<GoodsOrderDto>(PATHS.goodsOrders, request);
  return response.data;
}

export async function getGoodsOrderById(id: number): Promise<GoodsOrderDto> {
  const response = await getBeClient().get<GoodsOrderDto>(PATHS.goodsOrderById(id));
  return response.data;
}

export async function getMyGoodsOrders(
  params?: GoodsOrderListParams
): Promise<PageResponse<GoodsOrderDto>> {
  const response = await getBeClient().get<PageResponse<GoodsOrderDto>>(PATHS.goodsOrdersMe, {
    params,
  });
  return response.data;
}

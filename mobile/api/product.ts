/**
 * product.ts — 상품 API 함수
 */
import { getBeClient } from './be-client';
import type { ProductDetailResponse } from './types';

export async function getProduct(id: string): Promise<ProductDetailResponse> {
  const res = await getBeClient().get<ProductDetailResponse>(`/products/${id}`);
  return res.data;
}

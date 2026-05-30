/**
 * product.ts — 상품 API 함수
 */
import { getBeClient } from './be-client';
import type { ProductCategory, ProductDetailResponse, ProductListResponse } from './types';

export async function getProduct(id: string): Promise<ProductDetailResponse> {
  const res = await getBeClient().get<ProductDetailResponse>(`/products/${id}`);
  return res.data;
}

export async function getProducts(
  page: number,
  size: number,
  category?: ProductCategory
): Promise<ProductListResponse> {
  const res = await getBeClient().get<ProductListResponse>('/products', {
    params: { page, size, ...(category !== undefined ? { category } : {}) },
  });
  return res.data;
}

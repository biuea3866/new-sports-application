/**
 * products.ts — 상품 도메인 API 함수
 *
 * BE 경로:
 *   GET /products          — 상품 검색
 *   GET /products/popular  — 인기 상품 목록
 *   GET /products/{id}     — 상품 상세
 */
import { getBeClient } from './be-client';
import { PATHS } from './paths';
import { type PageResponse } from './facilities';

// ─── DTO 타입 ────────────────────────────────────────────────────────────────

export interface ProductSearchParams {
  keyword?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  page?: number;
  size?: number;
}

export interface ProductDto {
  id: number;
  name: string;
  category: string;
  price: number;
  stockCount: number;
  imageUrl: string | null;
  description: string;
  rating: number;
  reviewCount: number;
}

export interface ProductDetailDto extends ProductDto {
  images: string[];
  specifications: Record<string, string>;
}

// ─── API 함수 ────────────────────────────────────────────────────────────────

export async function searchProducts(
  params?: ProductSearchParams
): Promise<PageResponse<ProductDto>> {
  const response = await getBeClient().get<PageResponse<ProductDto>>(PATHS.products, { params });
  return response.data;
}

export async function getPopularProducts(): Promise<ProductDto[]> {
  const response = await getBeClient().get<ProductDto[]>(PATHS.productsPopular);
  return response.data;
}

export async function getProductById(id: number): Promise<ProductDetailDto> {
  const response = await getBeClient().get<ProductDetailDto>(PATHS.productById(id));
  return response.data;
}

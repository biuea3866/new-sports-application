/**
 * useProducts.ts — 상품 도메인 react-query 훅
 */
import { useQuery, type UseQueryOptions } from '@tanstack/react-query';
import {
  searchProducts,
  getPopularProducts,
  getProductById,
  type ProductSearchParams,
  type ProductDto,
  type ProductDetailDto,
} from '../products';
import { type PageResponse } from '../facilities';
import { productsKeys } from '../queryKeys';

export function useProductsQuery(
  params?: ProductSearchParams,
  options?: Omit<UseQueryOptions<PageResponse<ProductDto>>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: productsKeys.list(params ?? {}),
    queryFn: () => searchProducts(params),
    ...options,
  });
}

export function usePopularProductsQuery(
  options?: Omit<UseQueryOptions<ProductDto[]>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: productsKeys.popular(),
    queryFn: getPopularProducts,
    ...options,
  });
}

export function useProductDetailQuery(
  id: number,
  options?: Omit<UseQueryOptions<ProductDetailDto>, 'queryKey' | 'queryFn'>
) {
  return useQuery({
    queryKey: productsKeys.detail(id),
    queryFn: () => getProductById(id),
    enabled: id > 0,
    ...options,
  });
}

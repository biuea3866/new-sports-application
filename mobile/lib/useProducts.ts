/**
 * useProducts — GET /products TanStack Query 훅
 */
import { useQuery } from '@tanstack/react-query';
import { getProducts } from '../api/product';
import type { ProductCategory, ProductListResponse } from '../api/types';

export function useProducts(page: number, size: number, category?: ProductCategory) {
  return useQuery<ProductListResponse, Error>({
    queryKey: ['products', 'list', page, size, category ?? null],
    queryFn: () => getProducts(page, size, category),
  });
}

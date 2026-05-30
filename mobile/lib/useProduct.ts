/**
 * useProduct — GET /products/{id} TanStack Query 훅
 */
import { useQuery } from '@tanstack/react-query';
import { getProduct } from '../api/product';
import type { ProductDetailResponse } from '../api/types';

export function useProduct(id: string) {
  return useQuery<ProductDetailResponse, Error>({
    queryKey: ['products', id],
    queryFn: () => getProduct(id),
    enabled: id.length > 0,
  });
}

/**
 * useCatalogSearch — GET /api/catalog TanStack Query 훅
 *
 * 서버 상태는 Query 캐시가 SSOT — 스토어 복사 금지.
 * queryKey에 keyword·itemType·sellerType·page·size를 포함해 파라미터별로 캐시한다.
 */
import { useQuery } from '@tanstack/react-query';
import { getCatalog } from '../api/catalog';
import type { CatalogSearchCriteria, CatalogSearchResponse } from '../api/catalog-types';

export function useCatalogSearch(criteria: CatalogSearchCriteria) {
  const { keyword, itemType, sellerType, page, size } = criteria;

  return useQuery<CatalogSearchResponse, Error>({
    queryKey: ['catalog', 'search', keyword ?? null, itemType ?? null, sellerType ?? null, page, size],
    queryFn: () => getCatalog(criteria),
  });
}

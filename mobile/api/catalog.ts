/**
 * catalog.ts — 통합 검색(catalog) API 함수
 */
import { getBeClient } from './be-client';
import type { CatalogSearchCriteria, CatalogSearchResponse } from './catalog-types';

export async function getCatalog(criteria: CatalogSearchCriteria): Promise<CatalogSearchResponse> {
  const { keyword, itemType, sellerType, page, size } = criteria;
  const res = await getBeClient().get<CatalogSearchResponse>('/api/catalog', {
    params: {
      page,
      size,
      ...(keyword !== undefined ? { keyword } : {}),
      ...(itemType !== undefined ? { itemType } : {}),
      ...(sellerType !== undefined ? { sellerType } : {}),
    },
  });
  return res.data;
}

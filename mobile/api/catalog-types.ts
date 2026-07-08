/**
 * catalog-types.ts — 통합 검색(catalog) 응답/요청 타입
 *
 * BE 계약과 필드·타입 1:1 일치 (설계 문서 "응답 타입" 섹션 참조).
 */

export type CatalogItemType = 'PRODUCT' | 'LIMITED_DROP' | 'TICKET' | 'PROGRAM' | 'RECRUITMENT';

export type SellerType = 'B2C' | 'B2B';

export interface CatalogItem {
  itemType: CatalogItemType;
  sourceId: number;
  title: string;
  price: number | null; // KRW. TICKET 등 대표가 없으면 null → "가격 상세 확인"
  sellerType: SellerType | null; // PRODUCT만 값
  status: string; // 원본 status enum name
  detailPath: string; // 예: "/products/123"
  createdAt: string; // ISO-8601
}

export interface CatalogSearchResponse {
  items: CatalogItem[];
  page: number;
  size: number;
  failedDomains: CatalogItemType[];
}

export interface CatalogSearchCriteria {
  keyword?: string;
  itemType?: CatalogItemType;
  sellerType?: SellerType;
  page: number;
  size: number;
}

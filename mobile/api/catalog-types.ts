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
  // 같은 제목의 서로 다른 항목(예: 시설 4곳의 동일명 프로그램)을 구분하는 부가 표시 정보.
  // 유형마다 의미 있는 값이 없으면 null — 내부 식별자(sourceId 등)로 대체하지 않는다.
  locationName: string | null; // PROGRAM은 시설명, TICKET은 경기장명. 그 외 null
  scheduledAt: string | null; // ISO-8601. TICKET은 경기 시작 일시, RECRUITMENT는 모임 활동 일시. 그 외 null
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

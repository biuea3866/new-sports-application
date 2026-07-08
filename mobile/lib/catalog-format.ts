/**
 * catalog-format — 통합 검색(catalog) 화면이 공용으로 쓰는 순수 포맷 유틸.
 *
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ①".
 * 화면·카드 컴포넌트는 렌더링에만 집중하도록 가격 포맷·itemType 라벨을 이 유틸로 분리한다.
 * 상대 시각 포맷은 기존 관례(`lib/post-format.ts#formatRelativeTime`)를 그대로 재사용한다.
 */
import type { CatalogItemType } from '../api/catalog-types';

const PRICE_UNAVAILABLE_LABEL = '가격 상세 확인';

/** 가격을 표시 문자열로 변환한다. price=null(TICKET 등 대표가 없음)이면 안내 문구를 반환한다. */
export function formatCatalogPrice(price: number | null): string {
  if (price === null) {
    return PRICE_UNAVAILABLE_LABEL;
  }
  return `${price.toLocaleString()}원`;
}

/** `CatalogItem.itemType`에 대한 한글 배지 라벨. */
export const CATALOG_ITEM_TYPE_LABEL: Record<CatalogItemType, string> = {
  PRODUCT: '상품',
  LIMITED_DROP: '한정판',
  TICKET: '티켓',
  PROGRAM: '클래스',
  RECRUITMENT: '모집',
};

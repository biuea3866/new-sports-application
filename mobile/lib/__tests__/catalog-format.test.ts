/**
 * catalog-format — 통합 검색(catalog) 화면이 공용으로 쓰는 순수 가격 포맷 유틸 검증.
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ①"
 * (price=null → "가격 상세 확인", 그 외 KRW toLocaleString + "원").
 */
import { CATALOG_ITEM_TYPE_LABEL, formatCatalogPrice } from '../catalog-format';

describe('formatCatalogPrice', () => {
  it('price가 있으면 천 단위 구분자와 함께 원 단위로 표기한다', () => {
    expect(formatCatalogPrice(32000)).toBe('32,000원');
  });

  it('price=null이면 가격 상세 확인을 반환한다', () => {
    expect(formatCatalogPrice(null)).toBe('가격 상세 확인');
  });

  it('price=0이면 0원을 반환한다', () => {
    expect(formatCatalogPrice(0)).toBe('0원');
  });
});

describe('CATALOG_ITEM_TYPE_LABEL', () => {
  it('itemType별 한글 라벨을 정의한다', () => {
    expect(CATALOG_ITEM_TYPE_LABEL.PRODUCT).toBe('상품');
    expect(CATALOG_ITEM_TYPE_LABEL.LIMITED_DROP).toBe('한정판');
    expect(CATALOG_ITEM_TYPE_LABEL.TICKET).toBe('티켓');
    expect(CATALOG_ITEM_TYPE_LABEL.PROGRAM).toBe('클래스');
    expect(CATALOG_ITEM_TYPE_LABEL.RECRUITMENT).toBe('모집');
  });
});

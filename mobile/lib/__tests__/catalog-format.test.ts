/**
 * catalog-format — 통합 검색(catalog) 화면이 공용으로 쓰는 순수 가격 포맷 유틸 검증.
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` "텍스트 와이어프레임 ①"
 * (price=null → "가격 상세 확인", 그 외 KRW toLocaleString + "원").
 */
import {
  CATALOG_ITEM_TYPE_LABEL,
  formatCatalogDistinguisher,
  formatCatalogPrice,
} from '../catalog-format';

describe('formatCatalogPrice — 무료(0원) 표기', () => {
  // 통합 카탈로그에 무료 모집이 `0원` 으로 찍혔다. 같은 값을 `31-모집-목록` 은 `무료` 로
  // 표시하므로 화면마다 표기가 갈렸다 — 0을 실제 금액처럼 보여주는 것이 이번 갤러리 결함의
  // 원죄(19-결제-수단-선택 이 금액 없이 `0원`)와 같은 계열이라 기준을 하나로 맞춘다.
  it('0이면 "무료"로 표기한다', () => {
    expect(formatCatalogPrice(0)).toBe('무료');
  });

  it('0이 아닌 금액은 천 단위 구분자와 원 단위로 표기한다', () => {
    expect(formatCatalogPrice(45000)).toBe('45,000원');
  });

  it('가격이 없으면(null·undefined) 안내 문구를 유지한다 — 무료와 구분한다', () => {
    expect(formatCatalogPrice(null)).not.toBe('무료');
    expect(formatCatalogPrice(undefined)).not.toBe('무료');
  });
});

describe('formatCatalogPrice', () => {
  it('price가 있으면 천 단위 구분자와 함께 원 단위로 표기한다', () => {
    expect(formatCatalogPrice(32000)).toBe('32,000원');
  });

  it('price=null이면 가격 상세 확인을 반환한다', () => {
    expect(formatCatalogPrice(null)).toBe('가격 상세 확인');
  });

  // 과거 이 테스트는 `0원`을 단언해 결함을 고정하고 있었다 — 통합 카탈로그에 무료 모집이
  // `0원`으로 찍혀 실제 금액처럼 읽혔고, 같은 값을 모집 목록은 `무료`로 표시해 표기가 갈렸다.
  it('price=0이면 무료를 반환한다 (0원으로 찍지 않는다)', () => {
    expect(formatCatalogPrice(0)).toBe('무료');
    expect(formatCatalogPrice(0)).not.toBe('0원');
  });

  // 서버가 price 필드 자체를 생략해 보내면 undefined가 들어온다 — 화면이 죽지 않아야 한다.
  it('price 필드가 없으면(undefined) 가격 상세 확인을 반환한다', () => {
    expect(formatCatalogPrice(undefined)).toBe('가격 상세 확인');
  });
});

describe('formatCatalogDistinguisher', () => {
  // 회귀 방지: 통합 카탈로그(11-통합-카탈로그)에서 시설 4곳이 같은 이름의 프로그램을 등록해
  // "주말 정기 레슨" 카드가 3회 반복돼 보이는 결함 — locationName·scheduledAt으로 구분한다.
  it('locationName만 있으면 그대로 반환한다(PROGRAM: 시설명)', () => {
    expect(formatCatalogDistinguisher('루틴 피트니스 강남점', null)).toBe('루틴 피트니스 강남점');
  });

  it('scheduledAt만 있으면 절대 일시로 포맷해 반환한다(RECRUITMENT: 모임 활동 일시)', () => {
    expect(formatCatalogDistinguisher(null, '2026-08-10T19:00:00+09:00')).toBe('8월 10일 19:00');
  });

  it('locationName·scheduledAt이 모두 있으면 가운데점으로 이어붙인다(TICKET: 경기장·시작 일시)', () => {
    expect(formatCatalogDistinguisher('잠실종합운동장', '2026-08-10T19:00:00+09:00')).toBe(
      '잠실종합운동장 · 8월 10일 19:00'
    );
  });

  it('둘 다 없으면 null을 반환해 화면이 줄을 생략하게 한다(PRODUCT·LIMITED_DROP)', () => {
    expect(formatCatalogDistinguisher(null, null)).toBeNull();
  });

  it('locationName·scheduledAt이 undefined여도(서버 필드 생략) 죽지 않고 null을 반환한다', () => {
    expect(formatCatalogDistinguisher(undefined, undefined)).toBeNull();
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

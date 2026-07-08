/**
 * 판별자(itemType/orderType) + sourceId → 앱 상세 라우트 매핑 순수 함수 검증.
 * BE `detailPath`는 웹 경로 형태일 수 있어 신뢰하지 않고, 판별자 기반 매핑을 1차 방식으로 쓴다
 * (design-fe-app.md "Open Questions"). 미지원 판별자는 null로 방어한다.
 */
import { resolveCatalogRoute, resolveOrderRoute } from '../catalog-navigation';

describe('resolveCatalogRoute', () => {
  it('PRODUCT + sourceId를 상품 상세 경로로 매핑한다', () => {
    expect(resolveCatalogRoute('PRODUCT', 123)).toBe('/product/123');
  });

  it('LIMITED_DROP + sourceId를 한정판 상세 경로로 매핑한다', () => {
    expect(resolveCatalogRoute('LIMITED_DROP', 7)).toBe('/limited-drop/7');
  });

  it('TICKET + sourceId를 이벤트 상세 경로로 매핑한다', () => {
    expect(resolveCatalogRoute('TICKET', 42)).toBe('/event/42');
  });

  it('PROGRAM + sourceId를 시설 상세 경로로 매핑한다', () => {
    expect(resolveCatalogRoute('PROGRAM', 9)).toBe('/facility/9');
  });

  it('RECRUITMENT + sourceId를 모집 상세 경로로 매핑한다', () => {
    expect(resolveCatalogRoute('RECRUITMENT', 5)).toBe('/recruitments/5');
  });

  it('알 수 없는 itemType이면 null을 반환한다', () => {
    // 런타임 방어 대상(신규 BE itemType 추가 등) — 타입 시스템 밖 값을 unknown으로 통과시켜 검증한다.
    expect(resolveCatalogRoute('UNKNOWN_TYPE' as never, 1)).toBeNull();
  });
});

describe('resolveOrderRoute', () => {
  it('BOOKING + sourceId를 시설 상세 경로로 매핑한다', () => {
    expect(resolveOrderRoute('BOOKING', 11)).toBe('/facility/11');
  });

  it('TICKETING + sourceId를 이벤트 상세 경로로 매핑한다', () => {
    expect(resolveOrderRoute('TICKETING', 22)).toBe('/event/22');
  });

  it('GOODS + sourceId를 상품 상세 경로로 매핑한다', () => {
    expect(resolveOrderRoute('GOODS', 33)).toBe('/product/33');
  });

  it('RECRUITMENT + sourceId를 모집 상세 경로로 매핑한다', () => {
    expect(resolveOrderRoute('RECRUITMENT', 44)).toBe('/recruitments/44');
  });

  it('알 수 없는 orderType이면 null을 반환한다', () => {
    expect(resolveOrderRoute('UNKNOWN_TYPE' as never, 1)).toBeNull();
  });
});

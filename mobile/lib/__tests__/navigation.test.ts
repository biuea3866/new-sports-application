/**
 * navigation.ts ROUTES.limitedDrop — 한정판 라우트 경로 생성 검증
 * 근거: FE-08 티켓 "테스트 케이스"
 */
import { ROUTES } from '../navigation';

describe('ROUTES.limitedDrop', () => {
  it('detail(id)가 한정판 상세 경로 문자열을 생성한다', () => {
    expect(ROUTES.limitedDrop.detail('5')).toBe('/limited-drop/5');
  });

  it('purchase(id)가 한정판 구매 경로 문자열을 생성한다', () => {
    expect(ROUTES.limitedDrop.purchase('5')).toBe('/limited-drop/5/purchase');
  });
});

describe('ROUTES.catalog / ROUTES.orders', () => {
  it('catalog가 통합 검색 경로 문자열을 반환한다', () => {
    expect(ROUTES.catalog).toBe('/catalog');
  });

  it('orders가 통합 주문 내역 경로 문자열을 반환한다', () => {
    expect(ROUTES.orders).toBe('/orders');
  });
});

describe('ROUTES.queue', () => {
  it("waiting('limited-drop', '42')가 한정판 대기실 경로 문자열을 생성한다", () => {
    expect(ROUTES.queue.waiting('limited-drop', '42')).toBe('/queue/limited-drop/42');
  });

  it("waiting('ticketing-event', '7')가 티케팅 이벤트 대기실 경로 문자열을 생성한다", () => {
    expect(ROUTES.queue.waiting('ticketing-event', '7')).toBe('/queue/ticketing-event/7');
  });

  it('targetId에 특수문자가 포함돼도 그대로 경로에 치환한다', () => {
    expect(ROUTES.queue.waiting('limited-drop', 'abc-123_XYZ')).toBe(
      '/queue/limited-drop/abc-123_XYZ'
    );
  });
});

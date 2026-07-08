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

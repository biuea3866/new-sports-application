/**
 * catalog-navigation.ts — 판별자(itemType/orderType) + sourceId → 앱 상세 라우트 매핑 유틸
 *
 * 통합 검색(FE-09)·통합 주문내역(FE-10) 화면이 공유하는 선행 유틸(FE-12).
 * BE `CatalogItem.detailPath`/`OrderHistoryItem.detailPath`는 원본 도메인이 만든 문자열이라
 * 앱 라우트 형태와 어긋날 수 있다(웹 경로 형태 가능성 — design-fe-app.md "Open Questions").
 * 이 유틸은 detailPath 문자열을 파싱하지 않고, 판별자 + sourceId로 기존 `ROUTES`를
 * 직접 매핑하는 방식을 1차 방식으로 채택한다. 순수 함수 — 부수효과 없음.
 *
 * itemType↔ROUTES, orderType↔ROUTES 매핑은 이 유틸이 단독 소유한다(컴포넌트에 분기 로직 금지).
 */

import { ROUTES } from './navigation';
import type { CatalogItemType } from '../api/catalog-types';
import type { OrderType } from '../api/order-history-types';

/**
 * 모집(recruitment) 상세 경로 — `ROUTES`에는 아직 항목이 없어(FE-11 범위 밖) 로컬로 구성한다.
 * `app/recruitments/[id].tsx` 실제 라우트와 일치.
 */
function recruitmentDetailPath(sourceId: number): string {
  return `/recruitments/${sourceId}`;
}

/**
 * 통합 검색 결과 항목(itemType) + sourceId를 원본 상세 경로로 매핑한다.
 * 매핑 불가(알 수 없는 판별자)면 null을 반환한다 — 호출부는 이동을 무시해야 한다.
 */
export function resolveCatalogRoute(itemType: CatalogItemType, sourceId: number): string | null {
  switch (itemType) {
    case 'PRODUCT':
      return ROUTES.product.detail(String(sourceId));
    case 'LIMITED_DROP':
      return ROUTES.limitedDrop.detail(String(sourceId));
    case 'TICKET':
      return ROUTES.event.detail(String(sourceId));
    case 'PROGRAM':
      return ROUTES.facility.detail(String(sourceId));
    case 'RECRUITMENT':
      return recruitmentDetailPath(sourceId);
    default:
      return null;
  }
}

/**
 * 통합 주문내역 항목(orderType) + sourceId를 원본 상세 경로로 매핑한다.
 * 매핑 불가(알 수 없는 판별자)면 null을 반환한다 — 호출부는 이동을 무시해야 한다.
 */
export function resolveOrderRoute(orderType: OrderType, sourceId: number): string | null {
  switch (orderType) {
    case 'BOOKING':
      return ROUTES.facility.detail(String(sourceId));
    case 'TICKETING':
      return ROUTES.event.detail(String(sourceId));
    case 'GOODS':
      return ROUTES.product.detail(String(sourceId));
    case 'RECRUITMENT':
      return recruitmentDetailPath(sourceId);
    default:
      return null;
  }
}

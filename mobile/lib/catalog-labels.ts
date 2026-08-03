/**
 * 경기 상태·상품 카테고리의 한글 라벨 — 화면 공용 단일 출처.
 *
 * 스토어 탭은 경기 상태를 `오픈` 배지로 한글화했는데 같은 데이터를 쓰는 홈 피드만 `OPEN` 원문을
 * 노출하던 결함(01-모바일앱/03 캡쳐)이 있었다. 화면마다 매핑을 두면 한쪽만 한글화된 채 남으므로
 * 라벨을 여기 모으고 화면은 이 함수를 경유한다.
 *
 * 라벨 함수는 모르는 값을 만나도 예외를 던지지 않고 원문으로 떨어뜨린다 — BE enum 이 넓어졌을 때
 * 화면 전체가 죽는 대신 그 셀만 원문으로 남아 원인을 추적할 수 있다.
 */
import type { EventStatus, ProductCategory } from '../api/types';

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  SCHEDULED: '예정',
  OPEN: '오픈',
  CLOSED: '종료',
};

export const PRODUCT_CATEGORY_LABELS: Record<ProductCategory, string> = {
  EQUIPMENT: '장비',
  APPAREL: '의류',
  FOOTWEAR: '신발',
  ACCESSORY: '액세서리',
};

function labelOf(labels: Record<string, string>, value: string): string {
  if (value.length === 0) return '';
  return labels[value] ?? value;
}

/** 경기 상태 한글 라벨. */
export function eventStatusLabel(status: string): string {
  return labelOf(EVENT_STATUS_LABELS, status);
}

/** 상품 카테고리 한글 라벨. */
export function productCategoryLabel(category: string): string {
  return labelOf(PRODUCT_CATEGORY_LABELS, category);
}

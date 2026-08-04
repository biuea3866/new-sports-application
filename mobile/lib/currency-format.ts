/**
 * 원화 금액 표기.
 *
 * BE 가 BigDecimal 을 문자열로 내려주므로(장바구니 unitPrice·subtotal·totalAmount 등)
 * 문자열·숫자 양쪽을 받아 "29,000원" 형태로 만든다. 숫자로 해석할 수 없으면 "0원"으로
 * 수렴시켜 화면에 NaN 이 새지 않게 한다.
 */
export function formatCurrency(amount: string | number): string {
  const numericAmount = typeof amount === 'number' ? amount : Number(amount);
  const safeAmount = Number.isFinite(numericAmount) ? numericAmount : 0;
  return `${safeAmount.toLocaleString('ko-KR')}원`;
}

/**
 * 구역 대표가 표기 (이벤트 상세 "구역별 좌석").
 *
 * BE `SectionAvailability.minPrice`/`maxPrice`를 받아, 구역 내 좌석 가격이 모두 같으면
 * 단일가를, 등급이 다르면 최저가 기준 "~" 범위로 표기한다.
 */
export function formatPriceRange(minPrice: string | number, maxPrice: string | number): string {
  const min = typeof minPrice === 'number' ? minPrice : Number(minPrice);
  const max = typeof maxPrice === 'number' ? maxPrice : Number(maxPrice);
  const safeMin = Number.isFinite(min) ? min : 0;
  const safeMax = Number.isFinite(max) ? max : 0;
  if (safeMin === safeMax) return formatCurrency(safeMin);
  return `${formatCurrency(safeMin)}~`;
}

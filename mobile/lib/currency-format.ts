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

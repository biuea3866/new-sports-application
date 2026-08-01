/**
 * 결제 라우트 파라미터(amount) 해석.
 *
 * 결제 금액은 "모르면 0"이 아니라 "모르면 없음"이다 — 파라미터가 없거나 숫자가 아니거나
 * 0 이하이면 null 을 돌려, 화면이 0원을 실제 금액처럼 렌더하지 않게 한다
 * (유즈케이스 캡쳐 19-결제-수단-선택 회귀).
 */
export function parsePaymentAmount(rawAmount: string | string[] | undefined): number | null {
  if (typeof rawAmount !== 'string') {
    return null;
  }
  const parsedAmount = Number.parseInt(rawAmount, 10);
  if (Number.isNaN(parsedAmount) || parsedAmount <= 0) {
    return null;
  }
  return parsedAmount;
}

/**
 * 원화 금액 표시 — 천단위 구분 + `원` 단위.
 *
 * 단위 없는 맨 숫자는 그 값이 통화인지 비율인지 알 수 없어 오독을 부른다
 * (실제로 "상품당 평균 매출"이 단위 없이 찍혀 회전율 비율로 오해받았다).
 * 소수는 최대 2자리까지만 노출한다 — 원 단위 집계에서 그 이상은 의미가 없다.
 */
export function formatKrw(amount: number): string {
  return `${amount.toLocaleString("ko-KR", { maximumFractionDigits: 2 })}원`;
}

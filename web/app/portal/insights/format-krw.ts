/**
 * 원화 금액 표시 — 천단위 구분 + `원` 단위, 소수 없음.
 *
 * 단위 없는 맨 숫자는 그 값이 통화인지 비율인지 알 수 없어 오독을 부른다
 * (실제로 "상품당 평균 매출"이 단위 없이 찍혀 회전율 비율로 오해받았다).
 * 원은 소수 단위가 없다 — 소수점 2자리까지 노출하던 `26,333.33원` 표기가 옆 칸과 값이 같아
 * 보이는 착시를 더 키웠다(굿즈 KPI 라벨 결함). 정수로 반올림해 표시한다.
 */
export function formatKrw(amount: number): string {
  return `${amount.toLocaleString("ko-KR", { maximumFractionDigits: 0 })}원`;
}

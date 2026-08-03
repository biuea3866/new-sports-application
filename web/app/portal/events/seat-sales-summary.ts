/**
 * 경기 목록 카드의 좌석 판매 현황 문구.
 *
 * 목록 응답에 좌석 집계가 빠지면 `판매 {undefined} / {undefined}석`이 그대로 렌더돼
 * "판매 / 석"만 남은 채 숫자가 통째로 사라진다. 값이 없으면 문구 자체를 생략해
 * 깨진 문자열 대신 아무것도 보이지 않게 한다(0석은 정상 값이므로 그대로 표시한다).
 */
export function formatSeatSalesSummary(
  soldSeats: number | undefined,
  totalSeats: number | undefined
): string | null {
  if (!Number.isFinite(soldSeats) || !Number.isFinite(totalSeats)) return null;
  return `판매 ${soldSeats} / ${totalSeats}석`;
}

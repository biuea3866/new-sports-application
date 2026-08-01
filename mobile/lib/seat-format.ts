/**
 * 좌석 라벨 포맷.
 *
 * 좌석번호(seatNo)에 구역명이 이미 포함돼 들어오는 데이터가 있어(예: section "A석" +
 * seatNo "A석-01"), 화면이 구역명을 다시 앞에 붙이면 "A석-1-A석-01" 처럼 중복된다
 * (유즈케이스 캡쳐 17-이벤트-좌석-선택). 표기 단계에서 구역 접두사를 한 번만 남긴다.
 */

/** seatNo 앞에 붙은 구역명(구분자 유무 모두)을 제거한다. */
function stripSectionPrefix(section: string, seatNo: string): string {
  if (section.length === 0 || !seatNo.startsWith(section)) {
    return seatNo;
  }
  return seatNo.slice(section.length).replace(/^[-\s]+/, '');
}

/** 좌석 버튼용 짧은 라벨 — "A석-1-01". */
export function formatSeatLabel(section: string, rowNo: string, seatNo: string): string {
  const seatNumber = stripSectionPrefix(section, seatNo);
  return [section, rowNo, seatNumber].filter((part) => part.length > 0).join('-');
}

/** 주문 확인·접근성용 서술형 라벨 — "A석구역 1열 01번". */
export function formatSeatDescription(section: string, rowNo: string, seatNo: string): string {
  const seatNumber = stripSectionPrefix(section, seatNo);
  const parts: string[] = [];
  if (section.length > 0) parts.push(`${section}구역`);
  if (rowNo.length > 0) parts.push(`${rowNo}열`);
  if (seatNumber.length > 0) parts.push(`${seatNumber}번`);
  return parts.join(' ');
}

/**
 * program-format — 시설상품(program) 화면이 공용으로 쓰는 순수 포맷 유틸.
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * A-F1("PT 1:1 · 50,000원 · 60분", "정원 1명"). 화면·컴포넌트는 렌더링에만 집중하도록
 * 금액·소요시간·정원 표시 문자열 계산을 이 유틸로 분리한다.
 */

/** 시설상품 가격을 표시 문자열로 변환한다. */
export function formatProgramPrice(price: number): string {
  return `${price.toLocaleString()}원`;
}

/** 시설상품 소요시간(분)을 표시 문자열로 변환한다. */
export function formatProgramDuration(durationMinutes: number): string {
  return `${durationMinutes}분`;
}

/** 시설상품 정원을 표시 문자열로 변환한다. */
export function formatProgramCapacity(capacity: number): string {
  return `정원 ${capacity}명`;
}

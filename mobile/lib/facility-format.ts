/**
 * facility-format — 시설 표시·검색 순수 유틸.
 *
 * BE에 시설 키워드 검색 API가 없어(`GET /facilities`는 지역·종류 필터만 지원) 이름 검색은
 * 불러온 목록에 대한 클라이언트 필터로 처리한다. 판정 로직을 컴포넌트 밖에 둔다.
 */
import type { FacilityResponse } from '../api/types';

type FacilitySummary = Pick<FacilityResponse, 'name' | 'gu' | 'type'>;

/** 목록 행 부제 — "강남구 풋살장"처럼 지역과 종류를 한 줄로 보여준다. */
export function formatFacilityMetaLine(facility: FacilitySummary): string {
  return [facility.gu, facility.type].filter((part) => part.trim().length > 0).join(' ');
}

/** 이름·지역·종류 중 하나라도 검색어를 포함하면 매칭으로 본다(대소문자·공백 무시). */
export function filterFacilitiesByKeyword<T extends FacilitySummary>(
  facilities: T[],
  keyword: string
): T[] {
  const normalizedKeyword = keyword.trim().toLowerCase();
  if (normalizedKeyword.length === 0) {
    return facilities;
  }
  return facilities.filter((facility) =>
    [facility.name, facility.gu, facility.type].some((field) =>
      field.toLowerCase().includes(normalizedKeyword)
    )
  );
}

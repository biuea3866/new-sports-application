/**
 * 시설 `location`("lat,lng" 문자열)을 대기질 조회에 쓸 좌표로 파싱한다.
 * 이 파싱 로직은 컴포넌트(상세 페이지)에 인라인하지 않고 유틸로 분리한다(`no-logic-in-component`).
 * BFF 라우트(`web/app/api/portal/facilities/route.ts#toBeFacilityPayload`)의
 * 동일한 "lat,lng" 파싱 관례를 따른다.
 */
export interface ParsedLocation {
  lat: number;
  lng: number;
}

export function parseLocation(location: string | null | undefined): ParsedLocation | null {
  if (!location) {
    return null;
  }

  const [latRaw, lngRaw] = location.split(",").map((value) => value.trim());
  const lat = Number(latRaw);
  const lng = Number(lngRaw);

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return null;
  }

  return { lat, lng };
}

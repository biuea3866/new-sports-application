/**
 * 시설 목록·상세에서 시/도명을 표시용 문자열로 변환한다.
 * BE는 주소 파싱에 실패한 시설의 시도명을 "미지정"으로 보존한다
 * (FacilityRegion.UNSPECIFIED, backend/domain/facility/vo/FacilityRegion.kt).
 * FE는 값이 없거나 "미지정"이면 사용자에게 "지역 미확인"으로 표시한다.
 */
export function resolveSidoDisplayName(sidoName: string | null | undefined): string {
  if (!sidoName || sidoName === "미지정") {
    return "지역 미확인";
  }
  return sidoName;
}

/**
 * 시/도 표준코드 상수.
 * 코드값은 BE `regions` seed(design-db.md §3, V43__create_regions.sql)와 반드시 일치해야 한다.
 * 시/도 단위 개편은 극히 드물어(PRD Non-Goal) FE 정적 상수로 관리한다.
 */

export interface SidoOption {
  code: string;
  name: string;
}

export const SIDO_OPTIONS: SidoOption[] = [
  { code: "11", name: "서울특별시" },
  { code: "26", name: "부산광역시" },
  { code: "27", name: "대구광역시" },
  { code: "28", name: "인천광역시" },
  { code: "29", name: "광주광역시" },
  { code: "30", name: "대전광역시" },
  { code: "31", name: "울산광역시" },
  { code: "36", name: "세종특별자치시" },
  { code: "41", name: "경기도" },
  { code: "42", name: "강원특별자치도" },
  { code: "43", name: "충청북도" },
  { code: "44", name: "충청남도" },
  { code: "45", name: "전북특별자치도" },
  { code: "46", name: "전라남도" },
  { code: "47", name: "경상북도" },
  { code: "48", name: "경상남도" },
  { code: "50", name: "제주특별자치도" },
];

/** 시/도 미선택 시 서버 주소 파싱으로 자동 보간됨을 나타내는 기본 옵션. */
export const EMPTY_SIDO_OPTION: SidoOption = { code: "", name: "선택 안 함" };

/**
 * CSV → 시설 일괄 등록 파싱·검증.
 * 순수 함수. 서버/클라이언트 모두 사용 가능.
 *
 * CSV 컬럼 순서(헤더 행 필수):
 *   code,name,sido,gu,type,address,lat,lng,parking,tel,homePage,eduYn,meta
 *
 * sido는 optional이다 — 미입력 시 서버가 address로 시/도를 보간한다.
 */

export type FacilityType = "INDOOR" | "OUTDOOR" | "MIXED";

const FACILITY_TYPES: ReadonlySet<string> = new Set(["INDOOR", "OUTDOOR", "MIXED"]);

export interface CsvFacilityRow {
  /** 원본 1-indexed 행 번호 (헤더 제외) */
  rowIndex: number;
  code: string;
  name: string;
  /** 시/도 표준코드 (2자리). optional — 미입력 시 서버가 주소로 보간한다. */
  sido: string | undefined;
  gu: string;
  type: FacilityType;
  address: string;
  /** "lat,lng" 문자열 — BFF POST body의 location 필드 */
  location: string;
  parking: boolean;
  tel: string;
  homePage: string | undefined;
  eduYn: boolean;
  meta: string | undefined;
}

export interface CsvRowError {
  rowIndex: number;
  /** 원본 CSV 행 텍스트 */
  rawLine: string;
  errors: string[];
}

export interface ParseCsvResult {
  valid: CsvFacilityRow[];
  errors: CsvRowError[];
}

/**
 * CSV 텍스트를 파싱해 유효 행과 에러 행으로 분리한다.
 * - 첫 번째 행은 헤더로 처리해 스킵한다.
 * - 빈 행은 무시한다.
 * - 필드 내 쉼표는 큰따옴표로 감쌀 수 있다 (RFC 4180 단순 서브셋).
 */
export function parseCsvFacilities(csvText: string): ParseCsvResult {
  const lines = csvText.split(/\r?\n/);

  const valid: CsvFacilityRow[] = [];
  const errors: CsvRowError[] = [];

  // 헤더 행 스킵 (첫 번째 비공백 행)
  let headerSkipped = false;
  let dataRowIndex = 0;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed === "") continue;

    if (!headerSkipped) {
      headerSkipped = true;
      continue;
    }

    dataRowIndex++;
    const fields = splitCsvLine(trimmed);
    const rowErrors: string[] = [];

    const code = (fields[0] ?? "").trim();
    const name = (fields[1] ?? "").trim();
    const sido = (fields[2] ?? "").trim() || undefined;
    const gu = (fields[3] ?? "").trim();
    const typeRaw = (fields[4] ?? "").trim().toUpperCase();
    const address = (fields[5] ?? "").trim();
    const latRaw = (fields[6] ?? "").trim();
    const lngRaw = (fields[7] ?? "").trim();
    const parkingRaw = (fields[8] ?? "").trim().toLowerCase();
    const tel = (fields[9] ?? "").trim();
    const homePage = (fields[10] ?? "").trim() || undefined;
    const eduYnRaw = (fields[11] ?? "").trim().toLowerCase();
    const metaRaw = (fields[12] ?? "").trim() || undefined;

    if (!code) rowErrors.push("code: 필수 값이 없습니다.");
    if (!name) rowErrors.push("name: 필수 값이 없습니다.");
    if (!gu) rowErrors.push("gu: 필수 값이 없습니다.");
    if (!FACILITY_TYPES.has(typeRaw)) {
      rowErrors.push(`type: INDOOR|OUTDOOR|MIXED 중 하나여야 합니다. (입력값: "${fields[4] ?? ""}")`);
    }
    if (!address) rowErrors.push("address: 필수 값이 없습니다.");
    if (!tel) rowErrors.push("tel: 필수 값이 없습니다.");

    const lat = parseFloat(latRaw);
    const lng = parseFloat(lngRaw);

    if (latRaw === "" || isNaN(lat)) {
      rowErrors.push(`lat: 숫자여야 합니다. (입력값: "${latRaw}")`);
    }
    if (lngRaw === "" || isNaN(lng)) {
      rowErrors.push(`lng: 숫자여야 합니다. (입력값: "${lngRaw}")`);
    }

    const parking = parkingRaw === "true" || parkingRaw === "1" || parkingRaw === "y";
    const eduYn = eduYnRaw === "true" || eduYnRaw === "1" || eduYnRaw === "y";

    if (rowErrors.length > 0) {
      errors.push({ rowIndex: dataRowIndex, rawLine: trimmed, errors: rowErrors });
      continue;
    }

    valid.push({
      rowIndex: dataRowIndex,
      code,
      name,
      sido,
      gu,
      type: typeRaw as FacilityType,
      address,
      location: `${lat.toString()},${lng.toString()}`,
      parking,
      tel,
      homePage,
      eduYn,
      meta: metaRaw,
    });
  }

  return { valid, errors };
}

/**
 * RFC 4180 단순 서브셋 CSV 행 분리.
 * 큰따옴표로 감싸인 필드는 내부 쉼표를 허용한다.
 * 이스케이프: "" → "
 */
function splitCsvLine(line: string): string[] {
  const fields: string[] = [];
  let current = "";
  let inQuotes = false;
  let i = 0;

  while (i < line.length) {
    const ch = line[i];

    if (inQuotes) {
      if (ch === '"') {
        // 다음 문자도 " 이면 이스케이프
        if (line[i + 1] === '"') {
          current += '"';
          i += 2;
        } else {
          inQuotes = false;
          i++;
        }
      } else {
        current += ch;
        i++;
      }
    } else {
      if (ch === '"') {
        inQuotes = true;
        i++;
      } else if (ch === ",") {
        fields.push(current);
        current = "";
        i++;
      } else {
        current += ch;
        i++;
      }
    }
  }

  fields.push(current);
  return fields;
}

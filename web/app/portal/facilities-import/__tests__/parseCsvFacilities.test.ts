/**
 * parseCsvFacilities 단위 테스트
 */
import { describe, it, expect } from "vitest";
import { parseCsvFacilities } from "../parseCsvFacilities";

const VALID_HEADER = "code,name,sido,gu,type,address,lat,lng,parking,tel,homePage,eduYn,meta";

function csvOf(...dataRows: string[]): string {
  return [VALID_HEADER, ...dataRows].join("\n");
}

const VALID_ROW =
  "GN-01,강남 풋살장,11,강남구,INDOOR,서울특별시 강남구 테헤란로 1,37.5,127.0,true,02-1234-5678,https://example.com,false,{}";

describe("유효 행 파싱", () => {
  it("유효한 단일 행이 valid 배열에 포함된다", () => {
    const result = parseCsvFacilities(csvOf(VALID_ROW));
    expect(result.valid).toHaveLength(1);
    expect(result.errors).toHaveLength(0);

    const row = result.valid[0];
    expect(row?.code).toBe("GN-01");
    expect(row?.name).toBe("강남 풋살장");
    expect(row?.sido).toBe("11");
    expect(row?.gu).toBe("강남구");
    expect(row?.type).toBe("INDOOR");
    expect(row?.location).toBe("37.5,127");
    expect(row?.parking).toBe(true);
    expect(row?.eduYn).toBe(false);
    expect(row?.homePage).toBe("https://example.com");
    expect(row?.meta).toBe("{}");
  });
});

describe("헤더 행 스킵", () => {
  it("첫 번째 행(헤더)은 데이터로 파싱되지 않는다", () => {
    const result = parseCsvFacilities(csvOf(VALID_ROW));
    // valid 배열 rowIndex는 1부터 시작
    expect(result.valid[0]?.rowIndex).toBe(1);
  });
});

describe("빈 행 무시", () => {
  it("빈 행이 포함된 CSV에서 빈 행은 무시된다", () => {
    const csv = [VALID_HEADER, "", VALID_ROW, ""].join("\n");
    const result = parseCsvFacilities(csv);
    expect(result.valid).toHaveLength(1);
    expect(result.errors).toHaveLength(0);
  });
});

describe("필수 필드 누락 에러", () => {
  it("code가 빈 문자열이면 errors에 포함된다", () => {
    const row = ",강남 풋살장,11,강남구,INDOOR,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.errors).toEqual(
      expect.arrayContaining([expect.stringContaining("code")])
    );
  });

  it("gu가 빈 문자열이면 errors에 포함된다", () => {
    const row = "GN-01,강남 풋살장,11,,INDOOR,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.errors).toEqual(
      expect.arrayContaining([expect.stringContaining("gu")])
    );
  });

  it("tel이 빈 문자열이면 errors에 포함된다", () => {
    const row = "GN-01,강남 풋살장,11,강남구,INDOOR,서울특별시 강남구,37.5,127.0,true,,, false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.errors).toEqual(
      expect.arrayContaining([expect.stringContaining("tel")])
    );
  });
});

describe("sido optional 컬럼", () => {
  it("sido 값이 비어 있어도 오류 없이 파싱된다", () => {
    const row = "GN-01,강남 풋살장,,강남구,INDOOR,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.errors).toHaveLength(0);
    expect(result.valid).toHaveLength(1);
    expect(result.valid[0]?.sido).toBeUndefined();
  });

  it("sido 값이 있으면 row에 sido로 담긴다", () => {
    const row = "GN-01,강남 풋살장,26,해운대구,OUTDOOR,부산 해운대구,35.1,129.0,false,051-123-4567,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.valid[0]?.sido).toBe("26");
  });
});

describe("type enum 검증", () => {
  it("type이 INDOOR|OUTDOOR|MIXED가 아니면 errors에 포함된다", () => {
    const row = "GN-01,강남 풋살장,11,강남구,UNKNOWN,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.errors).toEqual(
      expect.arrayContaining([expect.stringContaining("type")])
    );
  });

  it("type은 소문자 입력도 대문자로 정규화해 유효 처리한다", () => {
    const row = "GN-01,강남 풋살장,11,강남구,outdoor,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.valid).toHaveLength(1);
    expect(result.valid[0]?.type).toBe("OUTDOOR");
  });
});

describe("위경도 numeric 검증", () => {
  it("lat이 비숫자이면 errors에 포함된다", () => {
    const row = "GN-01,강남 풋살장,11,강남구,INDOOR,서울특별시 강남구,abc,127.0,true,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.errors).toEqual(
      expect.arrayContaining([expect.stringContaining("lat")])
    );
  });

  it("lng가 빈 문자열이면 errors에 포함된다", () => {
    const row = "GN-01,강남 풋살장,11,강남구,INDOOR,서울특별시 강남구,37.5,,true,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.errors).toEqual(
      expect.arrayContaining([expect.stringContaining("lng")])
    );
  });

  it("유효한 lat/lng는 location 'lat,lng' 문자열로 변환된다", () => {
    const row = "GN-01,강남 풋살장,11,강남구,MIXED,서울특별시 강남구,37.123,127.456,false,02-9999-9999,,true,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.valid[0]?.location).toBe("37.123,127.456");
  });
});

describe("parking / eduYn boolean 파싱", () => {
  it.each([
    ["true", true],
    ["1", true],
    ["y", true],
    ["false", false],
    ["0", false],
    ["n", false],
    ["", false],
  ])("parking='%s' → %s", (parkingRaw, expected) => {
    const row = `GN-01,강남 풋살장,11,강남구,INDOOR,서울특별시 강남구,37.5,127.0,${parkingRaw},02-1234-5678,,false,`;
    const result = parseCsvFacilities(csvOf(row));
    expect(result.valid[0]?.parking).toBe(expected);
  });
});

describe("큰따옴표 감싸인 필드 (RFC 4180 서브셋)", () => {
  it("큰따옴표로 감싸인 필드 내 쉼표는 필드 구분자로 처리되지 않는다", () => {
    const row = `GN-01,강남 풋살장,11,강남구,INDOOR,"서울특별시, 강남구 테헤란로",37.5,127.0,true,02-1234-5678,,false,`;
    const result = parseCsvFacilities(csvOf(row));
    expect(result.valid).toHaveLength(1);
    expect(result.valid[0]?.address).toBe("서울특별시, 강남구 테헤란로");
  });

  it('큰따옴표 내 "" 이스케이프는 단일 "로 복원된다', () => {
    const row = `GN-01,"""강남"" 풋살장""",11,강남구,INDOOR,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,`;
    const result = parseCsvFacilities(csvOf(row));
    expect(result.valid[0]?.name).toBe('"강남" 풋살장"');
  });
});

describe("다중 행 — 유효/에러 혼합", () => {
  it("유효 행과 에러 행이 섞인 경우 각 배열에 올바르게 분리된다", () => {
    const validRow = "GN-01,강남 풋살장,11,강남구,INDOOR,서울특별시 강남구,37.5,127.0,true,02-1234-5678,,false,";
    const errorRow = ",강북 풋살장,11,강북구,INDOOR,서울특별시 강북구,37.6,127.1,false,02-5555-5555,,true,";
    const result = parseCsvFacilities(csvOf(validRow, errorRow));
    expect(result.valid).toHaveLength(1);
    expect(result.errors).toHaveLength(1);
    expect(result.errors[0]?.rowIndex).toBe(2);
  });
});

describe("homePage / meta 선택 필드", () => {
  it("homePage와 meta가 빈 문자열이면 undefined로 처리된다", () => {
    const row = "GN-01,강남 풋살장,11,강남구,OUTDOOR,서울특별시 강남구,37.5,127.0,false,02-1234-5678,,false,";
    const result = parseCsvFacilities(csvOf(row));
    expect(result.valid[0]?.homePage).toBeUndefined();
    expect(result.valid[0]?.meta).toBeUndefined();
  });
});

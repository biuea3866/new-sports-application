/**
 * 시설 폼 검증 단위 테스트
 * U-01: 필수 필드 누락 시 각 필드에 에러 메시지가 발생한다
 * U-02: 유효한 값이 모두 입력되면 validation이 통과한다
 * U-03: FacilityType 열거값 외의 값은 거부된다
 * U-04: 선택 필드(homePage, meta)는 없어도 통과한다
 */
import { describe, it, expect } from "vitest";
import { facilityFormSchema } from "../facility-form-schema";

const VALID_INPUT = {
  code: "GWANG-01",
  name: "광진 실내 체육관",
  gu: "광진구",
  type: "INDOOR" as const,
  address: "서울특별시 광진구 능동로 1",
  location: "37.5512,127.0699",
  parking: true,
  tel: "02-1234-5678",
  eduYn: false,
} as const;

describe("[U-01] 필수 필드 누락 시 에러 발생", () => {
  it("code가 빈 문자열이면 에러가 발생한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, code: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toContain("code");
    }
  });

  it("name이 빈 문자열이면 에러가 발생한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, name: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toContain("name");
    }
  });

  it("gu가 빈 문자열이면 에러가 발생한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, gu: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toContain("gu");
    }
  });

  it("address가 빈 문자열이면 에러가 발생한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, address: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toContain("address");
    }
  });

  it("location이 빈 문자열이면 에러가 발생한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, location: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toContain("location");
    }
  });

  it("tel이 빈 문자열이면 에러가 발생한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, tel: "" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toContain("tel");
    }
  });
});

describe("[U-02] 유효한 입력 시 validation 통과", () => {
  it("필수 필드가 모두 있으면 parse가 성공한다", () => {
    const result = facilityFormSchema.safeParse(VALID_INPUT);
    expect(result.success).toBe(true);
  });

  it("선택 필드(homePage, meta)가 없어도 통과한다", () => {
    const input = { ...VALID_INPUT };
    const result = facilityFormSchema.safeParse(input);
    expect(result.success).toBe(true);
  });
});

describe("[U-03] FacilityType 열거값 검증", () => {
  it.each(["INDOOR", "OUTDOOR", "MIXED"] as const)("type=%s은 유효하다", (type) => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, type });
    expect(result.success).toBe(true);
  });

  it("열거값 외의 type 값은 에러가 발생한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, type: "UNDERGROUND" });
    expect(result.success).toBe(false);
    if (!result.success) {
      const paths = result.error.issues.map((i) => i.path[0]);
      expect(paths).toContain("type");
    }
  });
});

describe("[U-04] 선택 필드 처리", () => {
  it("homePage가 있으면 그대로 통과한다", () => {
    const result = facilityFormSchema.safeParse({
      ...VALID_INPUT,
      homePage: "https://example.com",
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.homePage).toBe("https://example.com");
    }
  });

  it("meta가 있으면 그대로 통과한다", () => {
    const result = facilityFormSchema.safeParse({
      ...VALID_INPUT,
      meta: '{"extra": true}',
    });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.meta).toBe('{"extra": true}');
    }
  });
});

describe("[U-05] 시/도(sido) 선택 필드 처리", () => {
  it("sido 없이도 필수 필드가 유효하면 통과한다(하위 호환)", () => {
    const result = facilityFormSchema.safeParse(VALID_INPUT);
    expect(result.success).toBe(true);
  });

  it("sido가 빈 문자열이어도 통과한다(미선택 → 서버 주소 자동 판별)", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, sido: "" });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.sido).toBe("");
    }
  });

  it("sido에 유효한 표준코드가 있으면 그대로 통과한다", () => {
    const result = facilityFormSchema.safeParse({ ...VALID_INPUT, sido: "11" });
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.sido).toBe("11");
    }
  });
});

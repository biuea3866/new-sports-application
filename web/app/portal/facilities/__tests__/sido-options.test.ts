import { describe, it, expect } from "vitest";
import { SIDO_OPTIONS, EMPTY_SIDO_OPTION } from "../sido-options";

describe("SIDO_OPTIONS", () => {
  it("17개 시도 옵션을 제공한다", () => {
    expect(SIDO_OPTIONS).toHaveLength(17);
  });

  it("각 옵션의 code가 2자리 숫자 문자열이다", () => {
    SIDO_OPTIONS.forEach((option) => {
      expect(option.code).toMatch(/^\d{2}$/);
    });
  });

  it("서울(11)과 부산(26)을 포함한다", () => {
    expect(SIDO_OPTIONS).toContainEqual({ code: "11", name: "서울특별시" });
    expect(SIDO_OPTIONS).toContainEqual({ code: "26", name: "부산광역시" });
  });

  it("코드가 중복되지 않는다", () => {
    const codes = SIDO_OPTIONS.map((option) => option.code);
    expect(new Set(codes).size).toBe(codes.length);
  });

  it("선택 안 함 옵션은 빈 값 코드를 가진다", () => {
    expect(EMPTY_SIDO_OPTION).toEqual({ code: "", name: "선택 안 함" });
  });
});

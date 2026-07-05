import { describe, it, expect } from "vitest";
import { resolveSidoDisplayName } from "../sido-display";

describe("resolveSidoDisplayName", () => {
  it("시도명이 있으면 그대로 반환한다", () => {
    expect(resolveSidoDisplayName("서울특별시")).toBe("서울특별시");
  });

  it("시도명이 빈 문자열이면 지역 미확인을 반환한다", () => {
    expect(resolveSidoDisplayName("")).toBe("지역 미확인");
  });

  it("시도명이 미지정이면 지역 미확인을 반환한다", () => {
    expect(resolveSidoDisplayName("미지정")).toBe("지역 미확인");
  });

  it("시도명이 null 또는 undefined이면 지역 미확인을 반환한다", () => {
    expect(resolveSidoDisplayName(null)).toBe("지역 미확인");
    expect(resolveSidoDisplayName(undefined)).toBe("지역 미확인");
  });
});

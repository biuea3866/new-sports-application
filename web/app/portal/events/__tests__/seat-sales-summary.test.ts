import { describe, it, expect } from "vitest";
import { formatSeatSalesSummary } from "../seat-sales-summary";

describe("formatSeatSalesSummary", () => {
  it("판매 좌석과 총 좌석을 문구로 만든다", () => {
    expect(formatSeatSalesSummary(2, 90)).toBe("판매 2 / 90석");
  });

  it("0석은 정상 값이므로 그대로 표시한다", () => {
    expect(formatSeatSalesSummary(0, 0)).toBe("판매 0 / 0석");
  });

  it("좌석 집계가 누락되면 문구를 만들지 않는다", () => {
    expect(formatSeatSalesSummary(undefined, undefined)).toBeNull();
  });

  it("한쪽 값만 누락돼도 문구를 만들지 않는다", () => {
    expect(formatSeatSalesSummary(2, undefined)).toBeNull();
    expect(formatSeatSalesSummary(undefined, 90)).toBeNull();
  });
});

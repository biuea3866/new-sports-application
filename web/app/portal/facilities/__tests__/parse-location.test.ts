import { describe, it, expect } from "vitest";
import { parseLocation } from "../parse-location";

describe("parseLocation", () => {
  it("유효한 'lat,lng' 문자열을 숫자 좌표로 파싱한다", () => {
    expect(parseLocation("37.5,127.0")).toEqual({ lat: 37.5, lng: 127.0 });
  });

  it("공백이 섞여 있어도 파싱한다", () => {
    expect(parseLocation("37.5, 127.0")).toEqual({ lat: 37.5, lng: 127.0 });
  });

  it("숫자로 변환할 수 없으면 null을 반환한다", () => {
    expect(parseLocation("서울,어딘가")).toBeNull();
  });

  it("빈 문자열이면 null을 반환한다", () => {
    expect(parseLocation("")).toBeNull();
  });

  it("null·undefined이면 null을 반환한다", () => {
    expect(parseLocation(null)).toBeNull();
    expect(parseLocation(undefined)).toBeNull();
  });
});

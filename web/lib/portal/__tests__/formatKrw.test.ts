/**
 * formatKrw — 원화 표시 유틸 단위 테스트.
 *
 * 회귀 방지(p3-1): `app/portal/insights/format-krw.ts`에 갇혀 있어 `/portal/payments`의
 * `sellerAmount`·`pageSellerTotal` 표시가 `toLocaleString("ko-KR")` 기본값(소수 최대 3자리)에
 * 열려 있었다. `lib/portal/formatKrw.ts`로 옮겨 두 화면이 같은 규칙을 공유한다.
 */
import { describe, it, expect } from "vitest";
import { formatKrw } from "../formatKrw";

describe("formatKrw", () => {
  it("천단위 구분과 원 단위를 붙인다", () => {
    expect(formatKrw(158000)).toBe("158,000원");
  });

  it("소수는 반올림해서 정수로 표시한다", () => {
    expect(formatKrw(26333.33)).toBe("26,333원");
    expect(formatKrw(26333.49)).toBe("26,333원");
  });

  it("경계값 0.5원은 반올림해서 1원으로 표시한다", () => {
    expect(formatKrw(0.5)).toBe("1원");
  });

  it("0원도 원 단위를 붙여 표시한다", () => {
    expect(formatKrw(0)).toBe("0원");
  });
});

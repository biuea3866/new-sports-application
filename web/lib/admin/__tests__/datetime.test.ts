/**
 * 어드민 공통 날짜 포맷터 계약 테스트.
 *
 * BE는 `McpObjectMapperConfig`(@Primary, JsonInclude.NON_NULL)로 직렬화하므로
 * nullable 필드(`expiresAt`·`lastUsedAt`·`lastCalledAt`)가 null이면 JSON에서 **필드 자체가 생략**된다.
 * 따라서 FE는 `null`과 `undefined`를 동일하게 "값 없음"으로 다뤄야 하며,
 * 어떤 입력에도 화면에 `Invalid Date`가 새어 나가면 안 된다.
 */
import { describe, it, expect } from "vitest";
import { formatDateOnly, formatDateTime, EMPTY_DATE_TEXT } from "../datetime";

describe("formatDateOnly", () => {
  it("유효한 ISO 문자열을 사람이 읽는 날짜로 포맷한다", () => {
    const formatted = formatDateOnly("2026-01-02T00:00:00Z");

    expect(formatted).toContain("2026");
    expect(formatted).not.toContain("Invalid Date");
  });

  it("null이면 값 없음 표기를 반환한다", () => {
    expect(formatDateOnly(null)).toBe(EMPTY_DATE_TEXT);
  });

  it("undefined(BE NON_NULL 직렬화로 필드가 생략된 경우)면 값 없음 표기를 반환한다", () => {
    expect(formatDateOnly(undefined)).toBe(EMPTY_DATE_TEXT);
  });

  it("빈 문자열이면 값 없음 표기를 반환한다", () => {
    expect(formatDateOnly("")).toBe(EMPTY_DATE_TEXT);
  });

  it("파싱할 수 없는 문자열이면 Invalid Date 대신 값 없음 표기를 반환한다", () => {
    expect(formatDateOnly("날짜아님")).toBe(EMPTY_DATE_TEXT);
    expect(formatDateOnly("2026-13-45T99:99:99Z")).toBe(EMPTY_DATE_TEXT);
  });
});

describe("formatDateTime", () => {
  it("유효한 ISO 문자열을 사람이 읽는 일시로 포맷한다", () => {
    const formatted = formatDateTime("2026-05-01T10:00:00Z");

    expect(formatted).toContain("2026");
    expect(formatted).not.toContain("Invalid Date");
  });

  it("null·undefined·파싱 불가 입력은 값 없음 표기를 반환한다", () => {
    expect(formatDateTime(null)).toBe(EMPTY_DATE_TEXT);
    expect(formatDateTime(undefined)).toBe(EMPTY_DATE_TEXT);
    expect(formatDateTime("날짜아님")).toBe(EMPTY_DATE_TEXT);
  });
});

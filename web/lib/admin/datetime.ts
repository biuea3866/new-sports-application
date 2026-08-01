/**
 * 어드민 공통 날짜·일시 포맷터.
 *
 * BE는 `McpObjectMapperConfig`(@Primary)에 `JsonInclude.Include.NON_NULL`을 걸어 직렬화하므로
 * nullable 필드(`expiresAt`·`lastUsedAt`·`lastCalledAt`)가 null이면 **응답 JSON에서 키가 생략**된다.
 * 따라서 FE는 `null`과 `undefined`를 동일하게 "값 없음"으로 다뤄야 한다.
 *
 * `new Date(undefined)`는 `Invalid Date`를 만들고 `toLocaleDateString()`이 그대로
 * "Invalid Date" 문자열을 반환해 화면에 노출된다(06-MCP-토큰-관리 캡쳐 결함).
 * 이 모듈은 파싱 불가 입력을 전부 값 없음 표기로 흡수한다.
 */

/** 값이 없거나 파싱할 수 없을 때 표시하는 문자열. */
export const EMPTY_DATE_TEXT = "—";

type DateInput = string | null | undefined;

function toValidDate(value: DateInput): Date | null {
  if (value === null || value === undefined || value === "") return null;

  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/** ISO 문자열을 `YYYY. MM. DD.` 형태의 날짜로 포맷한다. 값이 없으면 `—`. */
export function formatDateOnly(value: DateInput): string {
  const date = toValidDate(value);
  if (date === null) return EMPTY_DATE_TEXT;

  return date.toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
}

/** ISO 문자열을 날짜+시각으로 포맷한다. 값이 없으면 `—`. */
export function formatDateTime(value: DateInput): string {
  const date = toValidDate(value);
  if (date === null) return EMPTY_DATE_TEXT;

  return date.toLocaleString("ko-KR");
}

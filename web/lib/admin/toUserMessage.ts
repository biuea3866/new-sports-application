/**
 * 어드민 공통 오류 → 사용자 메시지 변환.
 *
 * `ZodError.message`는 issue 배열을 JSON.stringify한 문자열이다. 이를 그대로 화면에 렌더하면
 * 운영자에게 `[{ "expected": "object", "code": "invalid_type", ... }]`가 노출된다
 * (05-피처플래그-감사로그 캡쳐 결함). 스키마 검증 실패는 내부 계약 문제이므로
 * 사람이 읽는 문장으로 치환하고, 원문은 콘솔에만 남긴다.
 */
import { ZodError } from "zod";

const UNKNOWN_MESSAGE = "알 수 없는 오류가 발생했습니다.";
const SCHEMA_MISMATCH_MESSAGE =
  "서버 응답 형식이 예상과 달라 화면을 표시할 수 없습니다. 잠시 후 다시 시도해 주세요.";

/** ZodError 원문(JSON 배열 문자열)이 Error.message에 실려 온 경우를 식별한다. */
function looksLikeZodIssueJson(message: string): boolean {
  const trimmed = message.trim();
  if (!trimmed.startsWith("[")) return false;

  return trimmed.includes('"code"') || trimmed.includes('"expected"') || trimmed.includes('"path"');
}

/**
 * 알 수 없는 예외 값을 사용자에게 보여줄 한 문장으로 변환한다.
 *
 * 스키마 검증 실패 상세는 화면에서 감추되 **콘솔로는 남긴다** — 사용자에게 raw JSON을
 * 노출하지 않으면서도 원인 추적 경로(어느 필드가 계약과 어긋났는지)를 잃지 않기 위함이다.
 */
export function toUserMessage(error: unknown): string {
  if (error instanceof ZodError) {
    console.error("[admin] 서버 응답이 스키마와 일치하지 않습니다.", error.issues);
    return SCHEMA_MISMATCH_MESSAGE;
  }

  if (error instanceof Error) {
    if (looksLikeZodIssueJson(error.message)) {
      console.error("[admin] 서버 응답이 스키마와 일치하지 않습니다.", error.message);
      return SCHEMA_MISMATCH_MESSAGE;
    }
    return error.message.length > 0 ? error.message : UNKNOWN_MESSAGE;
  }

  return UNKNOWN_MESSAGE;
}

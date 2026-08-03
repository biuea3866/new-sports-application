/**
 * 예외 → 사용자 메시지 변환의 공통 구현.
 *
 * `ZodError.message`는 issue 배열을 JSON.stringify한 문자열이라, 그대로 렌더하면 화면에
 * `[{ "code": "invalid_value", "path": ["sales", 0, "status"], ... }]`가 통째로 노출된다.
 * 스키마 검증 실패는 내부 계약 문제이므로 사람이 읽는 문장으로 치환하고 원문은 콘솔에만 남긴다.
 *
 * 어드민(05-피처플래그-감사로그)에서 먼저 겪은 결함이 포털 매출(14-매출-결제-내역)에서 재발해,
 * 화면군마다 복사하지 않도록 구현을 여기 하나로 모으고 로그 접두사만 주입받는다.
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
 * 화면군 이름(`admin`·`portal`)을 로그 접두사로 갖는 변환 함수를 만든다.
 *
 * 검증 실패 상세는 화면에서 감추되 **콘솔로는 남긴다** — 사용자에게 raw JSON을 노출하지
 * 않으면서도 어느 필드가 계약과 어긋났는지 추적할 경로를 잃지 않기 위함이다.
 */
export function createUserMessageMapper(scope: string): (error: unknown) => string {
  const logSchemaMismatch = (detail: unknown) => {
    console.error(`[${scope}] 서버 응답이 스키마와 일치하지 않습니다.`, detail);
  };

  return function toUserMessage(error: unknown): string {
    if (error instanceof ZodError) {
      logSchemaMismatch(error.issues);
      return SCHEMA_MISMATCH_MESSAGE;
    }

    if (error instanceof Error) {
      if (looksLikeZodIssueJson(error.message)) {
        logSchemaMismatch(error.message);
        return SCHEMA_MISMATCH_MESSAGE;
      }
      return error.message.length > 0 ? error.message : UNKNOWN_MESSAGE;
    }

    return UNKNOWN_MESSAGE;
  };
}

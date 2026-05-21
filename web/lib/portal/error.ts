/**
 * BE ProblemDetail(RFC 7807) 디코딩 및 사용자 친화 메시지 매핑.
 */
import "server-only";

export class PortalApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly userMessage: string,
    public readonly detail?: string
  ) {
    super(`PortalApiError(${status}): ${userMessage}`);
    this.name = "PortalApiError";
  }
}

const STATUS_MESSAGES: Record<number, string> = {
  400: "잘못된 요청입니다. 입력 값을 확인해 주세요.",
  401: "로그인이 필요합니다.",
  403: "해당 작업을 수행할 권한이 없습니다.",
  404: "요청한 리소스를 찾을 수 없습니다.",
  409: "이미 존재하거나 충돌이 발생했습니다.",
  500: "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.",
};

function defaultMessage(status: number): string {
  return (
    STATUS_MESSAGES[status] ??
    "오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
  );
}

/** BE Response를 받아 에러 상태이면 PortalApiError를 throw한다. */
export async function throwIfErrorResponse(response: Response): Promise<void> {
  if (response.ok) return;

  let detail: string | undefined;
  try {
    const body = (await response.json()) as { detail?: string };
    detail = body.detail;
  } catch {
    // JSON 파싱 실패 시 detail 없이 진행
  }

  throw new PortalApiError(response.status, defaultMessage(response.status), detail);
}

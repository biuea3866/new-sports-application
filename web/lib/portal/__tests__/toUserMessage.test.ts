/**
 * 포털 공통 오류 → 사용자 메시지 변환 계약 테스트.
 *
 * 회귀 방지: 매출 화면이 `PartnerSalesResponseSchema.parse` 실패 시 ZodError.message(=issue
 * 배열 JSON 원문)를 그대로 렌더해, 화면 전체 폭에 `[{ "code": "invalid_value", "path": ... }]`가
 * 6줄로 노출됐다(02-파트너포털/14 캡쳐). 어드민(`lib/admin/toUserMessage.ts`)에서 이미 해결한
 * 방식을 포털에도 적용한다 — 화면엔 사람이 읽는 문장, 상세는 콘솔.
 */
import { describe, it, expect, vi, afterEach } from "vitest";
import { z, ZodError } from "zod";
import { toUserMessage } from "../toUserMessage";

function makeZodError(): ZodError {
  const schema = z.object({ status: z.enum(["PENDING", "COMPLETED"]) });
  const result = schema.safeParse({ status: "READY" });
  if (result.success) throw new Error("픽스처가 검증 실패를 만들지 못했습니다.");
  return result.error;
}

describe("포털 toUserMessage", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("일반 Error는 메시지를 그대로 전달한다", () => {
    expect(toUserMessage(new Error("매출 내역을 불러오지 못했습니다."))).toBe(
      "매출 내역을 불러오지 못했습니다."
    );
  });

  it("ZodError는 raw JSON 대신 사람이 읽는 메시지로 치환한다", () => {
    const message = toUserMessage(makeZodError());

    expect(message).not.toContain("invalid_value");
    expect(message).not.toContain('"code"');
    expect(message).not.toContain('"path"');
    expect(message).not.toContain("[{");
    expect(message).toContain("응답");
  });

  it("ZodError를 감싼 Error도 raw JSON을 노출하지 않는다", () => {
    const wrapped = new Error(makeZodError().message);

    expect(toUserMessage(wrapped)).not.toContain("invalid_value");
  });

  it("Error가 아닌 값은 기본 메시지를 반환한다", () => {
    expect(toUserMessage("문자열 예외")).toBe("알 수 없는 오류가 발생했습니다.");
    expect(toUserMessage(null)).toBe("알 수 없는 오류가 발생했습니다.");
  });

  // 사용자에게 숨긴 상세는 원인 추적을 위해 콘솔로 남긴다.
  it("검증 실패 상세를 콘솔 오류로 남긴다", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    toUserMessage(makeZodError());

    expect(consoleError).toHaveBeenCalled();
    expect(JSON.stringify(consoleError.mock.calls[0] ?? [])).toContain("invalid_value");
  });

  it("일반 Error는 콘솔 오류를 남기지 않는다", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);

    toUserMessage(new Error("네트워크 오류"));

    expect(consoleError).not.toHaveBeenCalled();
  });
});

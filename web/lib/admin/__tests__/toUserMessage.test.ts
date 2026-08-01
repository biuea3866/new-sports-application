/**
 * 어드민 공통 오류 → 사용자 메시지 변환 계약 테스트.
 *
 * ZodError의 `.message`는 issue 배열을 그대로 JSON.stringify한 문자열이라,
 * 그대로 화면에 뿌리면 운영자에게 `[{ "expected": "object", "code": "invalid_type" ... }]`가 노출된다
 * (05-피처플래그-감사로그 캡쳐 결함). 검증 실패는 사람이 읽는 메시지로 치환해야 한다.
 */
import { describe, it, expect } from "vitest";
import { z, ZodError } from "zod";
import { toUserMessage } from "../toUserMessage";

function makeZodError(): ZodError {
  const schema = z.object({ before: z.object({ key: z.string() }) });
  const result = schema.safeParse({});
  if (result.success) throw new Error("픽스처가 검증 실패를 만들지 못했습니다.");
  return result.error;
}

describe("toUserMessage", () => {
  it("일반 Error는 메시지를 그대로 전달한다", () => {
    expect(toUserMessage(new Error("변경 이력을 불러오지 못했습니다."))).toBe(
      "변경 이력을 불러오지 못했습니다."
    );
  });

  it("ZodError는 raw JSON 대신 사람이 읽는 메시지로 치환한다", () => {
    const message = toUserMessage(makeZodError());

    expect(message).not.toContain("invalid_type");
    expect(message).not.toContain("expected");
    expect(message).not.toContain("[{");
    expect(message).not.toContain('"code"');
    expect(message.length).toBeGreaterThan(0);
  });

  it("ZodError 메시지는 응답 형식 문제임을 알린다", () => {
    expect(toUserMessage(makeZodError())).toContain("응답");
  });

  it("Error가 아닌 값은 기본 메시지를 반환한다", () => {
    expect(toUserMessage("문자열 예외")).toBe("알 수 없는 오류가 발생했습니다.");
    expect(toUserMessage(null)).toBe("알 수 없는 오류가 발생했습니다.");
    expect(toUserMessage(undefined)).toBe("알 수 없는 오류가 발생했습니다.");
  });

  it("ZodError를 감싼 Error도 raw JSON을 노출하지 않는다", () => {
    const wrapped = new Error(makeZodError().message);

    expect(toUserMessage(wrapped)).not.toContain("invalid_type");
  });
});

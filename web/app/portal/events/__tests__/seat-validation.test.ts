/**
 * [U-01] 좌석 입력 검증 단위 테스트
 * CreateEventInputSchema: seats 1 ≤ count ≤ 500
 */
import { describe, it, expect } from "vitest";
import { CreateEventInputSchema } from "@/lib/portal/schemas";

const BASE_INPUT = {
  title: "테스트 경기",
  venue: "서울월드컵경기장",
  startsAt: "2026-06-01T10:00:00.000Z",
};

describe("[U-01] CreateEventInputSchema 좌석 입력 검증", () => {
  it("좌석 1개 입력 시 검증에 통과한다", () => {
    const result = CreateEventInputSchema.safeParse({
      ...BASE_INPUT,
      seats: ["A1"],
    });
    expect(result.success).toBe(true);
  });

  it("좌석 500개 입력 시 검증에 통과한다 (최대 허용)", () => {
    const seats = Array.from({ length: 500 }, (_, i) => `A${i + 1}`);
    const result = CreateEventInputSchema.safeParse({ ...BASE_INPUT, seats });
    expect(result.success).toBe(true);
  });

  it("좌석 0개(빈 배열) 입력 시 검증에 실패한다", () => {
    const result = CreateEventInputSchema.safeParse({ ...BASE_INPUT, seats: [] });
    expect(result.success).toBe(false);
    if (!result.success) {
      const fieldErrors = result.error.flatten().fieldErrors;
      expect(fieldErrors["seats"]).toBeDefined();
    }
  });

  it("좌석 501개 입력 시 검증에 실패한다 (최대 초과)", () => {
    const seats = Array.from({ length: 501 }, (_, i) => `A${i + 1}`);
    const result = CreateEventInputSchema.safeParse({ ...BASE_INPUT, seats });
    expect(result.success).toBe(false);
    if (!result.success) {
      const fieldErrors = result.error.flatten().fieldErrors;
      expect(fieldErrors["seats"]).toBeDefined();
    }
  });

  it("seats 필드 누락 시 검증에 실패한다", () => {
    const result = CreateEventInputSchema.safeParse({
      title: "테스트",
      venue: "경기장",
      startsAt: "2026-06-01T10:00:00.000Z",
    });
    expect(result.success).toBe(false);
  });

  it("title 필드 누락 시 검증에 실패한다", () => {
    const result = CreateEventInputSchema.safeParse({
      venue: "경기장",
      startsAt: "2026-06-01T10:00:00.000Z",
      seats: ["A1"],
    });
    expect(result.success).toBe(false);
  });

  it("venue 필드 누락 시 검증에 실패한다", () => {
    const result = CreateEventInputSchema.safeParse({
      title: "테스트",
      startsAt: "2026-06-01T10:00:00.000Z",
      seats: ["A1"],
    });
    expect(result.success).toBe(false);
  });
});

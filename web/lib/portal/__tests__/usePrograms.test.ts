/**
 * @vitest-environment jsdom
 *
 * usePrograms 훅 + createProgram 액션 테스트.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";

import { usePrograms, createProgram } from "../usePrograms";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const sampleProgram = {
  id: 1,
  facilityId: "facility-001",
  ownerUserId: 1,
  name: "PT 1:1",
  description: null,
  price: 50000,
  capacity: 1,
  durationMinutes: 60,
};

describe("usePrograms", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("시설상품 목록을 success로 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([sampleProgram]));

    const { result } = renderHook(() => usePrograms("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual([sampleProgram]);
  });

  it("등록된 상품이 없으면(0건) success + 빈 배열을 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([]));

    const { result } = renderHook(() => usePrograms("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual([]);
  });

  it("조회 실패 시 error 상태를 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "서버 오류" }, 500));

    const { result } = renderHook(() => usePrograms("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("error");
    });

    expect(result.current.data).toBeNull();
  });
});

describe("createProgram", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("유효한 입력이면 등록 후 생성된 시설상품을 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(sampleProgram, 201));

    const result = await createProgram("facility-001", {
      name: "PT 1:1",
      price: 50000,
      capacity: 1,
      durationMinutes: 60,
    });

    expect(result).toEqual(sampleProgram);
  });

  it("BE가 실패 응답을 반환하면 에러를 throw한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "잘못된 요청입니다." }, 400));

    await expect(
      createProgram("facility-001", {
        name: "PT 1:1",
        price: 50000,
        capacity: 1,
        durationMinutes: 60,
      })
    ).rejects.toThrow("잘못된 요청입니다.");
  });

  it("가격이 음수이면 요청 전 zod 검증에서 즉시 실패한다(BE 호출 없음)", async () => {
    await expect(
      createProgram("facility-001", {
        name: "PT 1:1",
        price: -1000,
        capacity: 1,
        durationMinutes: 60,
      })
    ).rejects.toThrow();

    expect(mockFetch).not.toHaveBeenCalled();
  });
});

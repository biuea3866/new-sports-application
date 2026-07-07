/**
 * @vitest-environment jsdom
 *
 * useHolidays 훅 + addHoliday/removeHoliday 액션 테스트.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";

import { useHolidays, addHoliday, removeHoliday } from "../useHolidays";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("useHolidays", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("시설 상세 응답에서 holidays를 추출해 success로 반환한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "facility-001", operatingHours: [], holidays: ["2026-07-15"] })
    );

    const { result } = renderHook(() => useHolidays("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual(["2026-07-15"]);
  });

  it("휴무일이 없으면(0건) success + 빈 배열을 반환한다 — 정상 상태", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "facility-001", operatingHours: [], holidays: [] })
    );

    const { result } = renderHook(() => useHolidays("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual([]);
  });

  it("BE 조회 실패 시 error 상태를 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "서버 오류" }, 500));

    const { result } = renderHook(() => useHolidays("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("error");
    });

    expect(result.current.data).toBeNull();
  });
});

describe("addHoliday / removeHoliday", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("addHoliday는 추가 후 갱신된 holidays 목록을 반환한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "facility-001", operatingHours: [], holidays: ["2026-07-15"] })
    );

    const result = await addHoliday("facility-001", "2026-07-15");

    expect(result).toEqual(["2026-07-15"]);
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/portal/facilities/facility-001/holidays");
    expect(init.method).toBe("POST");
  });

  it("removeHoliday는 query date로 DELETE 요청 후 갱신된 목록을 반환한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "facility-001", operatingHours: [], holidays: [] })
    );

    const result = await removeHoliday("facility-001", "2026-07-15");

    expect(result).toEqual([]);
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/portal/facilities/facility-001/holidays?date=2026-07-15");
    expect(init.method).toBe("DELETE");
  });

  it("삭제 실패 시 에러를 throw한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "권한이 없습니다." }, 403));

    await expect(removeHoliday("facility-001", "2026-07-15")).rejects.toThrow(
      "권한이 없습니다."
    );
  });
});

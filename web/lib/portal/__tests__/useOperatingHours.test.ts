/**
 * @vitest-environment jsdom
 *
 * useOperatingHours 훅 + updateOperatingHours 액션 테스트.
 * 운영시간은 전용 GET이 없어 시설 상세 응답(operatingHours 임베드)에서 추출한다.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";

import { useOperatingHours, updateOperatingHours } from "../useOperatingHours";
import type { OperatingHours } from "../types";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const sampleOperatingHours: OperatingHours[] = [
  {
    dayOfWeek: "MONDAY",
    openTime: "06:00",
    closeTime: "22:00",
    breaks: [],
    slotDurationMinutes: 60,
    capacity: 10,
  },
];

describe("useOperatingHours", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("시설 상세 응답에서 operatingHours를 추출해 success로 반환한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "facility-001", operatingHours: sampleOperatingHours, holidays: [] })
    );

    const { result } = renderHook(() => useOperatingHours("facility-001"));

    expect(result.current.status).toBe("loading");

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual(sampleOperatingHours);
    expect(mockFetch).toHaveBeenCalledWith(
      "/api/portal/facilities/facility-001",
      expect.anything()
    );
  });

  it("운영시간이 미등록(0건)이어도 success + 빈 배열을 반환한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "facility-001", operatingHours: [], holidays: [] })
    );

    const { result } = renderHook(() => useOperatingHours("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual([]);
  });

  it("BE 조회 실패 시 error 상태와 메시지를 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "권한이 없습니다." }, 403));

    const { result } = renderHook(() => useOperatingHours("facility-001"));

    await waitFor(() => {
      expect(result.current.status).toBe("error");
    });

    expect(result.current.data).toBeNull();
    expect(result.current.error).toBe("권한이 없습니다.");
  });
});

describe("updateOperatingHours", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("유효한 입력이면 PUT 호출 후 저장된 operatingHours를 반환한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ id: "facility-001", operatingHours: sampleOperatingHours, holidays: [] })
    );

    const result = await updateOperatingHours("facility-001", {
      operatingHours: sampleOperatingHours,
    });

    expect(result).toEqual(sampleOperatingHours);
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/portal/facilities/facility-001/operating-hours");
    expect(init.method).toBe("PUT");
  });

  it("BE가 실패 응답을 반환하면 에러를 throw한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "잘못된 요청입니다." }, 400));

    await expect(
      updateOperatingHours("facility-001", { operatingHours: sampleOperatingHours })
    ).rejects.toThrow("잘못된 요청입니다.");
  });
});

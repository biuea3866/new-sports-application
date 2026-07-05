/**
 * @vitest-environment jsdom
 *
 * useAirQuality 훅 테스트.
 * 좌표 입력에 따른 idle/loading/success/error 전이를 사용자 관점(반환값)으로 검증한다.
 * 근거 티켓: FE-06-web-airquality-bff-hook.md.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, waitFor } from "@testing-library/react";

import { useAirQuality } from "../useAirQuality";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const sampleGoodResponse = {
  pm10: 30,
  pm25: 12,
  pm10Grade: "GOOD",
  pm25Grade: "GOOD",
  representativeGrade: "GOOD",
  stationName: "강남구",
  measuredAt: "2026-07-05T10:00:00Z",
};

const sampleUnknownResponse = {
  pm10: null,
  pm25: null,
  pm10Grade: "UNKNOWN",
  pm25Grade: "UNKNOWN",
  representativeGrade: "UNKNOWN",
  stationName: null,
  measuredAt: null,
};

describe("useAirQuality", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("좌표가 주어지면 훅이 대기질 데이터를 success 상태로 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(sampleGoodResponse));

    const { result } = renderHook(() => useAirQuality(37.5, 127.0));

    expect(result.current.status).toBe("loading");

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual(sampleGoodResponse);
  });

  it("fetch 실패 시 훅이 error 상태를 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "서버 오류" }, 500));

    const { result } = renderHook(() => useAirQuality(37.5, 127.0));

    await waitFor(() => {
      expect(result.current.status).toBe("error");
    });

    expect(result.current.data).toBeNull();
  });

  it("lat/lng가 없으면(NaN) 훅이 조회하지 않고 idle을 유지한다", () => {
    const { result } = renderHook(() => useAirQuality(NaN, NaN));

    expect(result.current.status).toBe("idle");
    expect(result.current.data).toBeNull();
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("lat/lng가 null이어도 훅이 조회하지 않고 idle을 유지한다", () => {
    const { result } = renderHook(() => useAirQuality(null, null));

    expect(result.current.status).toBe("idle");
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it("BE가 UNKNOWN을 반환하면 훅이 해당 응답을 success로 담아 폴백 판단을 컴포넌트에 맡긴다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(sampleUnknownResponse));

    const { result } = renderHook(() => useAirQuality(0, 0));

    await waitFor(() => {
      expect(result.current.status).toBe("success");
    });

    expect(result.current.data).toEqual(sampleUnknownResponse);
  });
});

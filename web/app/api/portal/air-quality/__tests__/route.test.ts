/**
 * 대기질 BFF Route Handler 테스트.
 * BE 계약: GET /air-quality?lat&lng → 200 AirQualityResponse (실패 시에도 200+UNKNOWN).
 * 근거 티켓: FE-06-web-airquality-bff-hook.md.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Air Quality Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("BFF 라우트가 lat/lng 쿼리를 BE로 그대로 전달한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValue(
      new Response(
        JSON.stringify({
          pm10: 30,
          pm25: 12,
          pm10Grade: "GOOD",
          pm25Grade: "GOOD",
          representativeGrade: "GOOD",
          stationName: "강남구",
          measuredAt: "2026-07-05T10:00:00Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );

    const { GET } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/air-quality?lat=37.5&lng=127.0"
    );
    await GET(request);

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [calledUrl] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(calledUrl).toContain("/air-quality?");
    expect(calledUrl).toContain("lat=37.5");
    expect(calledUrl).toContain("lng=127.0");
  });

  it("BE 성공 응답을 그대로 forward한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const beBody = {
      pm10: 92,
      pm25: 41,
      pm10Grade: "BAD",
      pm25Grade: "MODERATE",
      representativeGrade: "BAD",
      stationName: "해운대구",
      measuredAt: "2026-07-05T14:00:00Z",
    };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(beBody), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { GET } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/air-quality?lat=35.16&lng=129.16"
    );
    const response = await GET(request);

    expect(response.status).toBe(200);
    const body = (await response.json()) as Record<string, unknown>;
    expect(body).toEqual(beBody);
  });

  it("BE가 실패해도 200 + UNKNOWN 응답을 그대로 forward한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const beBody = {
      pm10: null,
      pm25: null,
      pm10Grade: "UNKNOWN",
      pm25Grade: "UNKNOWN",
      representativeGrade: "UNKNOWN",
      stationName: null,
      measuredAt: null,
    };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(beBody), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { GET } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/air-quality?lat=0&lng=0"
    );
    const response = await GET(request);

    expect(response.status).toBe(200);
    const body = (await response.json()) as Record<string, unknown>;
    expect(body["representativeGrade"]).toBe("UNKNOWN");
  });

  it("BE 연결 실패 시 503을 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue(undefined),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));

    const { GET } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/air-quality?lat=37.5&lng=127.0"
    );
    const response = await GET(request);

    expect(response.status).toBe(503);
  });
});

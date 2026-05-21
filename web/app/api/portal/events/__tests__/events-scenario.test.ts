/**
 * [S-01] 경기 등록 + 501개 좌석 검증
 * [S-02] 좌석 501개 입력 시 BFF 클라이언트 측 검증 실패
 *
 * app/api/portal/events/route.ts 를 대상으로 한다.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("[S-01] 좌석 100개로 경기 등록 시 BE에 forward하고 201을 반환한다", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("좌석 100개 입력 시 BE에 forward하고 201을 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const seats = Array.from({ length: 100 }, (_, i) => `A${i + 1}`);
    const beResponseBody = {
      id: 1,
      title: "2026 K리그 결승전",
      venue: "서울월드컵경기장",
      startsAt: "2026-06-01T18:00:00.000Z",
      status: "SCHEDULED",
      ownerId: 1,
      totalSeats: 100,
      soldSeats: 0,
      availableSeats: 100,
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    };
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify(beResponseBody), {
        status: 201,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { POST } = await import("../route");
    const request = new NextRequest("http://localhost:3000/api/portal/events", {
      method: "POST",
      body: JSON.stringify({
        title: "2026 K리그 결승전",
        venue: "서울월드컵경기장",
        startsAt: "2026-06-01T18:00:00.000Z",
        seats,
      }),
      headers: { "Content-Type": "application/json" },
    });
    const response = await POST(request);

    expect(response.status).toBe(201);
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });
});

describe("[S-02] 좌석 501개 입력 시 클라이언트 측 검증 실패", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("좌석 501개 입력 시 BFF가 400을 반환하고 BE를 호출하지 않는다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const { POST } = await import("../route");
    const seats = Array.from({ length: 501 }, (_, i) => `A${i + 1}`);
    const request = new NextRequest("http://localhost:3000/api/portal/events", {
      method: "POST",
      body: JSON.stringify({
        title: "테스트 경기",
        venue: "경기장",
        startsAt: "2026-06-01T18:00:00.000Z",
        seats,
      }),
      headers: { "Content-Type": "application/json" },
    });
    const response = await POST(request);

    expect(response.status).toBe(400);
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

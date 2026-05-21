/**
 * BFF Route Handler 테스트 — Events [id]
 * GET /api/portal/events/[id] — 단건 + 판매 현황 조회
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Events [id] Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("[S-01] 경기 단건 조회 시 좌석 판매 현황을 포함한 응답을 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const detailBody = {
      id: 1,
      title: "2026 K리그 결승전",
      venue: "서울월드컵경기장",
      startsAt: "2026-06-01T18:00:00.000Z",
      status: "OPEN",
      ownerId: 1,
      totalSeats: 100,
      soldSeats: 3,
      availableSeats: 97,
      seats: [
        { id: 1, label: "A1", sold: true },
        { id: 2, label: "A2", sold: false },
        { id: 3, label: "A3", sold: true },
      ],
      createdAt: "2026-01-01T00:00:00Z",
      updatedAt: "2026-01-01T00:00:00Z",
    };
    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify(detailBody), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { GET } = await import("../route");
    const request = new NextRequest("http://localhost:3000/api/portal/events/1");
    const response = await GET(request, { params: { id: "1" } });

    expect(response.status).toBe(200);
    const body = (await response.json()) as typeof detailBody;
    expect(body.status).toBe("OPEN");
    expect(body.soldSeats).toBe(3);
    expect(body.seats).toHaveLength(3);
  });

  it("BE가 401을 반환하면 401 + WWW-Authenticate 헤더를 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue(undefined),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ title: "Unauthorized" }), {
        status: 401,
        headers: {
          "Content-Type": "application/json",
          "WWW-Authenticate": 'Bearer realm="api"',
        },
      })
    );

    const { GET } = await import("../route");
    const request = new NextRequest("http://localhost:3000/api/portal/events/1");
    const response = await GET(request, { params: { id: "1" } });

    expect(response.status).toBe(401);
    expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
  });

  it("BE가 500을 반환하면 500 + 사용자 친화 메시지를 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ title: "Internal Server Error" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { GET } = await import("../route");
    const request = new NextRequest("http://localhost:3000/api/portal/events/1");
    const response = await GET(request, { params: { id: "1" } });

    expect(response.status).toBe(500);
    const body = (await response.json()) as { message: string };
    expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
  });
});

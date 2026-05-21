/**
 * BFF Route Handler 테스트 — Events [id]/open
 * POST /api/portal/events/[id]/open — OPEN 전이 forward
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Events [id] open Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("[S-01] SCHEDULED 경기에 오픈 요청 시 BE에 forward하고 200을 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 1, status: "OPEN" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { POST } = await import("../route");
    const request = new NextRequest("http://localhost:3000/api/portal/events/1/open", {
      method: "POST",
    });
    const response = await POST(request, { params: { id: "1" } });

    expect(response.status).toBe(200);
    expect(mockFetch).toHaveBeenCalledTimes(1);
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

    const { POST } = await import("../route");
    const request = new NextRequest("http://localhost:3000/api/portal/events/1/open", {
      method: "POST",
    });
    const response = await POST(request, { params: { id: "1" } });

    expect(response.status).toBe(401);
    expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
  });
});

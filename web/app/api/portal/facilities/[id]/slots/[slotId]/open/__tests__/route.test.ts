/**
 * BFF Route Handler 테스트 — /api/portal/facilities/[id]/slots/[slotId]/open
 * PATCH : 슬롯 오픈 → BE PATCH /facilities/{facilityId}/slots/{slotId}/open forward
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Slot Open Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("BE에 forward하고 상태가 OPEN인 슬롯을 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const beResponseBody = {
      id: 2,
      facilityId: "facility-001",
      date: "2026-07-12T00:00:00Z",
      timeRange: "15:00-16:00",
      capacity: 8,
      ownerId: 1,
      status: "OPEN",
      programId: null,
    };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(beResponseBody), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { PATCH } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/slots/2/open",
      { method: "PATCH" }
    );
    const response = await PATCH(request, { params: { id: "facility-001", slotId: "2" } });

    expect(response.status).toBe(200);
    const [url] = mockFetch.mock.calls[0] as [string];
    expect(url).toBe("http://localhost:8080/facilities/facility-001/slots/2/open");
    const body = (await response.json()) as { status: string };
    expect(body.status).toBe("OPEN");
  });

  it("BE 401이면 401 + WWW-Authenticate 헤더를 forward한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue(undefined),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ title: "Unauthorized" }), {
        status: 401,
        headers: { "Content-Type": "application/json", "WWW-Authenticate": 'Bearer realm="api"' },
      })
    );

    const { PATCH } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/slots/2/open",
      { method: "PATCH" }
    );
    const response = await PATCH(request, { params: { id: "facility-001", slotId: "2" } });

    expect(response.status).toBe(401);
    expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
  });

  it("BE가 500을 반환하면 500 + 사용자 친화 메시지를 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ title: "Internal Server Error" }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { PATCH } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/slots/2/open",
      { method: "PATCH" }
    );
    const response = await PATCH(request, { params: { id: "facility-001", slotId: "2" } });

    expect(response.status).toBe(500);
    const body = (await response.json()) as { message: string };
    expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
  });
});

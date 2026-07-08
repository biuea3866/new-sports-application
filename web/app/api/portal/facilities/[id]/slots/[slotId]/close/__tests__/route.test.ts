/**
 * BFF Route Handler 테스트 — /api/portal/facilities/[id]/slots/[slotId]/close
 * PATCH : 슬롯 마감 → BE PATCH /facilities/{facilityId}/slots/{slotId}/close forward
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Slot Close Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  it("BE에 forward하고 상태가 CLOSED인 슬롯을 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    const beResponseBody = {
      id: 1,
      facilityId: "facility-001",
      date: "2026-07-12T00:00:00Z",
      timeRange: "14:00-15:00",
      capacity: 8,
      ownerId: 1,
      status: "CLOSED",
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
      "http://localhost:3000/api/portal/facilities/facility-001/slots/1/close",
      { method: "PATCH" }
    );
    const response = await PATCH(request, { params: { id: "facility-001", slotId: "1" } });

    expect(response.status).toBe(200);
    const [url] = mockFetch.mock.calls[0] as [string];
    expect(url).toBe("http://localhost:8080/facilities/facility-001/slots/1/close");
    const body = (await response.json()) as { status: string };
    expect(body.status).toBe("CLOSED");
  });

  it("BE가 403을 반환하면 403 + 권한 안내 메시지를 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ title: "Forbidden" }), {
        status: 403,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { PATCH } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/slots/1/close",
      { method: "PATCH" }
    );
    const response = await PATCH(request, { params: { id: "facility-001", slotId: "1" } });

    expect(response.status).toBe(403);
  });

  it("BE가 409(이미 CLOSED)를 반환하면 409 + 충돌 안내 메시지를 반환한다", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ title: "Conflict" }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { PATCH } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/portal/facilities/facility-001/slots/1/close",
      { method: "PATCH" }
    );
    const response = await PATCH(request, { params: { id: "facility-001", slotId: "1" } });

    expect(response.status).toBe(409);
    const body = (await response.json()) as { message: string };
    expect(body.message).toBe("이미 존재하거나 충돌이 발생했습니다.");
  });
});

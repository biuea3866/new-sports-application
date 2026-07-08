/**
 * BFF Route Handler 테스트 — /api/portal/facilities/[id]/programs
 * GET  : 시설상품 목록 조회 → BE GET /facilities/{facilityId}/programs forward
 * POST : 시설상품 등록 → BE POST /facilities/{facilityId}/programs forward
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Facility Programs Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("GET 시설상품 목록", () => {
    it("BE에 forward하고 목록을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/programs"
      );
      const response = await GET(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(200);
      const [url] = mockFetch.mock.calls[0] as [string];
      expect(url).toBe("http://localhost:8080/facilities/facility-001/programs");
    });

    it("0건이어도 정상(200) 응답이다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/programs"
      );
      const response = await GET(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(200);
      const body = (await response.json()) as unknown[];
      expect(body).toEqual([]);
    });
  });

  describe("POST 시설상품 등록", () => {
    it("유효한 body이면 BE에 forward하고 201을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: 1,
        facilityId: "facility-001",
        ownerUserId: 1,
        name: "PT 1:1",
        description: null,
        price: 50000,
        capacity: 1,
        durationMinutes: 60,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/programs",
        {
          method: "POST",
          body: JSON.stringify({
            name: "PT 1:1",
            price: 50000,
            capacity: 1,
            durationMinutes: 60,
          }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(201);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it("price가 음수이면 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/programs",
        {
          method: "POST",
          body: JSON.stringify({
            name: "PT 1:1",
            price: -1000,
            capacity: 1,
            durationMinutes: 60,
          }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("capacity가 0이면 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/programs",
        {
          method: "POST",
          body: JSON.stringify({
            name: "PT 1:1",
            price: 50000,
            capacity: 0,
            durationMinutes: 60,
          }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });
});

/**
 * BFF Route Handler 테스트 — /api/portal/facilities/[id]/holidays
 * POST   : 휴무일 추가 → BE POST /facilities/{facilityId}/holidays forward
 * DELETE : 휴무일 삭제 → BE DELETE /facilities/{facilityId}/holidays?date=... forward
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Facility Holidays Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("POST 휴무일 추가", () => {
    it("유효한 날짜이면 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(
          JSON.stringify({ id: "facility-001", operatingHours: [], holidays: ["2026-07-15"] }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/holidays",
        {
          method: "POST",
          body: JSON.stringify({ date: "2026-07-15" }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url] = mockFetch.mock.calls[0] as [string];
      expect(url).toBe("http://localhost:8080/facilities/facility-001/holidays");
    });

    it("date 형식이 yyyy-MM-dd가 아니면 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/holidays",
        {
          method: "POST",
          body: JSON.stringify({ date: "2026/07/15" }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("동일 날짜 중복 추가 요청도 BE에 forward되어 멱등하게 처리된다(에러 아님)", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(
          JSON.stringify({ id: "facility-001", operatingHours: [], holidays: ["2026-07-15"] }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/holidays",
        {
          method: "POST",
          body: JSON.stringify({ date: "2026-07-15" }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(200);
    });
  });

  describe("DELETE 휴무일 삭제", () => {
    it("query date를 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ id: "facility-001", operatingHours: [], holidays: [] }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { DELETE } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/holidays?date=2026-07-15",
        { method: "DELETE" }
      );
      const response = await DELETE(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(200);
      const [url] = mockFetch.mock.calls[0] as [string];
      expect(url).toBe(
        "http://localhost:8080/facilities/facility-001/holidays?date=2026-07-15"
      );
    });

    it("date query가 없으면 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { DELETE } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/facilities/facility-001/holidays",
        { method: "DELETE" }
      );
      const response = await DELETE(request, { params: { id: "facility-001" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });
});

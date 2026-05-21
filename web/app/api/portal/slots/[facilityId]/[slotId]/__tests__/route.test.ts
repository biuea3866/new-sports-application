/**
 * BFF Route Handler 테스트 — Portal Slots [facilityId]/[slotId]
 * PATCH  : UpdateSlotInputSchema 검증 + forward
 *   - happy path → 200
 *   - 빈 객체 입력 → 400 (refine 최소 1 필드)
 * DELETE : 슬롯 삭제 forward
 *   - happy path → 204
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Slots [facilityId]/[slotId] Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("PATCH 슬롯 수정", () => {
    it("[happy path] 유효한 부분 업데이트 body이면 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: 1,
        facilityId: "facility-001",
        date: "2026-06-01T00:00:00Z",
        timeRange: "10:00-11:00",
        capacity: 15,
        ownerId: 1,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PATCH } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001/1",
        {
          method: "PATCH",
          body: JSON.stringify({ timeRange: "10:00-11:00" }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await PATCH(request, {
        params: { facilityId: "facility-001", slotId: "1" },
      });

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it("빈 객체 입력 시 refine 거부 → 400을 반환한다 (최소 1 필드 필요)", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { PATCH } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001/1",
        {
          method: "PATCH",
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await PATCH(request, {
        params: { facilityId: "facility-001", slotId: "1" },
      });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("capacity만 수정하는 body이면 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: 1,
        facilityId: "facility-001",
        date: "2026-06-01T00:00:00Z",
        timeRange: "09:00-10:00",
        capacity: 20,
        ownerId: 1,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PATCH } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001/1",
        {
          method: "PATCH",
          body: JSON.stringify({ capacity: 20 }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await PATCH(request, {
        params: { facilityId: "facility-001", slotId: "1" },
      });

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });

  describe("DELETE 슬롯 삭제", () => {
    it("[happy path] DELETE 요청 시 BE에 forward하고 204를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(new Response(null, { status: 204 }));

      const { DELETE } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001/1",
        { method: "DELETE" }
      );
      const response = await DELETE(request, {
        params: { facilityId: "facility-001", slotId: "1" },
      });

      expect(response.status).toBe(204);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });
});

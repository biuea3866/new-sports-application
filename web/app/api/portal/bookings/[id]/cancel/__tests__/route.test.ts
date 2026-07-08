/**
 * BFF Route Handler 테스트 — Portal Bookings Cancel
 * POST /api/portal/bookings/[id]/cancel → BE POST /bookings/{id}/cancel forward
 *
 * - [S-01] happy path: 예약 취소 성공 → 200 + BookingResponse
 * - [S-02] 취소 불가 상태(409) → 사용자 친화 메시지
 * - [S-03] BE 401 → WWW-Authenticate 헤더 forward
 * - [S-04] BE 5xx → 500 + 사용자 친화 메시지
 * - [S-05] BACKEND_URL 미설정 → 503
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

const cancelledBooking = {
  id: 1,
  slotId: 10,
  userId: 1,
  status: "CANCELLED",
  paymentId: null,
  paymentStatus: null,
  createdAt: "2026-06-01T09:00:00Z",
  updatedAt: "2026-06-01T10:00:00Z",
};

describe("Portal Bookings Cancel Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-01] POST 예약 취소 — happy path", () => {
    it("BE가 200을 반환하면 취소된 예약을 그대로 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(cancelledBooking), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings/1/cancel", {
        method: "POST",
        body: JSON.stringify({ reason: "운영자 취소" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(200);
      const body = (await response.json()) as typeof cancelledBooking;
      expect(body.status).toBe("CANCELLED");
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const fetchCallArg = mockFetch.mock.calls[0]?.[0] as string;
      expect(fetchCallArg).toContain("/bookings/1/cancel");
    });

    it("reason 없이 빈 body로 요청해도 BE로 forward된다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(cancelledBooking), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings/1/cancel", {
        method: "POST",
        body: JSON.stringify({}),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(200);
    });
  });

  describe("[S-02] 취소 불가 상태 처리", () => {
    it("BE가 409를 반환하면 409 + 사용자 친화 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Conflict", detail: "이미 취소된 예약입니다." }), {
          status: 409,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings/1/cancel", {
        method: "POST",
        body: JSON.stringify({}),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(409);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("이미 존재하거나 충돌이 발생했습니다.");
    });
  });

  describe("[S-03] BE 401 응답 forward", () => {
    it("BE가 401을 반환하면 401 + WWW-Authenticate 헤더를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), {
          status: 401,
          headers: {
            "Content-Type": "application/json",
            "WWW-Authenticate": 'Bearer realm="api"',
          },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings/1/cancel", {
        method: "POST",
        body: JSON.stringify({}),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(401);
      expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });
  });

  describe("[S-04] BE 5xx 응답 처리", () => {
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

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings/1/cancel", {
        method: "POST",
        body: JSON.stringify({}),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });
  });

  describe("[S-05] BACKEND_URL 미설정", () => {
    it("BACKEND_URL이 없으면 503을 반환한다", async () => {
      delete process.env["BACKEND_URL"];

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings/1/cancel", {
        method: "POST",
        body: JSON.stringify({}),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(503);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
    });
  });
});

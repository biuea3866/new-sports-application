/**
 * BFF Route Handler 테스트 — Portal Bookings
 * GET  : 내 예약 목록 조회 forward
 *   - [S-02] happy path: 예약 현황 조회 → 200
 *   - 쿼리 파라미터(status, page, size) forward 확인
 * BE 401: WWW-Authenticate 헤더 forward
 * BE 5xx: 사용자 친화 메시지
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Bookings Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-02] GET 예약 현황 조회 — happy path", () => {
    it("예약 목록을 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const pageBody = {
        bookings: [
          {
            id: 1,
            slotId: 10,
            userId: 1,
            status: "CONFIRMED",
            paymentId: null,
            paymentStatus: null,
            createdAt: "2026-06-01T09:00:00Z",
            updatedAt: "2026-06-01T09:00:00Z",
          },
        ],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        size: 10,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(pageBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/bookings?page=0&size=10"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it("status 쿼리 파라미터를 포함하여 BE에 forward한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(
          JSON.stringify({
            bookings: [],
            totalElements: 0,
            totalPages: 0,
            page: 0,
            size: 10,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/bookings?status=PENDING&page=0&size=10"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      const fetchCallArg = mockFetch.mock.calls[0]?.[0] as string;
      expect(fetchCallArg).toContain("status=PENDING");
    });
  });

  describe("BE 401 응답 forward", () => {
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

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings");
      const response = await GET(request);

      expect(response.status).toBe(401);
      expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });
  });

  describe("BE 5xx 응답 처리", () => {
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

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/bookings");
      const response = await GET(request);

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });
  });
});

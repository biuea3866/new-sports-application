/**
 * BFF Route Handler 테스트 — Portal Payments
 * GET  : 내 결제 목록 조회 forward
 *   - [S-01] happy path: 결제 목록 조회 → 200
 *   - 쿼리 파라미터(status, paidAtFrom, paidAtTo, page, size) forward 확인
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

describe("Portal Payments Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-01] GET 결제 목록 조회 — happy path", () => {
    it("결제 목록을 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const pageBody = {
        content: [
          {
            id: 1,
            orderType: "BOOKING",
            orderId: 10,
            method: "KAKAO",
            amount: 50000,
            status: "COMPLETED",
            createdAt: "2026-06-01T09:00:00Z",
            paidAt: "2026-06-01T09:01:00Z",
            pgTransactionId: "pg-tx-001",
            provider: "kakao",
          },
        ],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(pageBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/payments?page=0&size=20"
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
          JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/payments?status=COMPLETED&page=0&size=20"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      const fetchCallArg = mockFetch.mock.calls[0]?.[0] as string;
      expect(fetchCallArg).toContain("status=COMPLETED");
    });

    it("paidAtFrom/paidAtTo 파라미터를 포함하여 BE에 forward한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(
          JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/payments?paidAtFrom=2026-06-01T00:00:00Z&paidAtTo=2026-06-30T23:59:59Z"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      const fetchCallArg = mockFetch.mock.calls[0]?.[0] as string;
      expect(fetchCallArg).toContain("paidAtFrom=");
      expect(fetchCallArg).toContain("paidAtTo=");
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
      const request = new NextRequest("http://localhost:3000/api/portal/payments");
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
      const request = new NextRequest("http://localhost:3000/api/portal/payments");
      const response = await GET(request);

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });
  });

  describe("BACKEND_URL 미설정", () => {
    it("BACKEND_URL이 없으면 503을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      delete process.env["BACKEND_URL"];

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/payments");
      const response = await GET(request);

      expect(response.status).toBe(503);
    });
  });
});

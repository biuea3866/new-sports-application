/**
 * BFF Route Handler 테스트 — Events
 * POST 입력 검증: CreateEventInputSchema happy path + 실패 케이스
 * GET 쿼리: 정상 forward → 200
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

describe("Portal Events Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("POST 입력 검증 (CreateEventInputSchema)", () => {
    it("[happy path] 유효한 body이면 BE에 forward하고 201을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: 1,
        title: "2026 K리그 결승전",
        venue: "서울월드컵경기장",
        startsAt: "2026-06-01T18:00:00Z",
        status: "SCHEDULED",
        ownerId: 1,
        totalSeats: 3,
        soldSeats: 0,
        availableSeats: 3,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/events", {
        method: "POST",
        body: JSON.stringify({
          title: "2026 K리그 결승전",
          venue: "서울월드컵경기장",
          startsAt: "2026-06-01T18:00:00Z",
          seats: ["A1", "A2", "A3"],
        }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(201);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it("좌석 0개(빈 배열)이면 zod 거부 → 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/events", {
        method: "POST",
        body: JSON.stringify({
          title: "이벤트",
          venue: "경기장",
          startsAt: "2026-06-01T18:00:00Z",
          seats: [],
        }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("좌석 501개이면 zod 거부 → 400을 반환한다 (최대 500개)", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const seats = Array.from({ length: 501 }, (_, i) => `A${i + 1}`);
      const request = new NextRequest("http://localhost:3000/api/portal/events", {
        method: "POST",
        body: JSON.stringify({
          title: "이벤트",
          venue: "경기장",
          startsAt: "2026-06-01T18:00:00Z",
          seats,
        }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("title 필드 누락 시 zod 거부 → 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/events", {
        method: "POST",
        body: JSON.stringify({
          venue: "경기장",
          startsAt: "2026-06-01T18:00:00Z",
          seats: ["A1"],
        }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });

  describe("GET 쿼리 forward", () => {
    it("정상 쿼리 파라미터를 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const pageBody = {
        content: [],
        page: 0,
        size: 10,
        totalElements: 0,
        totalPages: 0,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(pageBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/events?page=0&size=10");
      const response = await GET(request);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });

  describe("[S-01] BE 401 응답을 401 + WWW-Authenticate 헤더로 forward", () => {
    it("BE가 401을 반환하면 Route Handler도 401 + WWW-Authenticate 헤더를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      const beHeaders = new Headers({
        "Content-Type": "application/json",
        "WWW-Authenticate": 'Bearer realm="api"',
      });
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), {
          status: 401,
          headers: beHeaders,
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/events");
      const response = await GET(request);

      expect(response.status).toBe(401);
      expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });
  });

  describe("[S-02] BE 5xx 응답 시 사용자 친화 메시지 반환", () => {
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
      const request = new NextRequest("http://localhost:3000/api/portal/events");
      const response = await GET(request);

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });
  });
});

/**
 * BFF Route Handler 테스트 — Portal Slots [facilityId]
 * GET  : 시설별 슬롯 목록 forward
 * POST : 슬롯 등록 입력 검증 (CreateSlotInputSchema) + forward
 *   - [S-01] happy path: 슬롯 등록 → 201 반환
 *   - date 필드 누락 → 400
 *   - timeRange 필드 누락 → 400
 *   - capacity 0 이하 → 400
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

describe("Portal Slots [facilityId] Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("GET 슬롯 목록 forward", () => {
    it("facilityId로 슬롯 목록을 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const slotsBody = [
        {
          id: 1,
          facilityId: "facility-001",
          date: "2026-06-01T00:00:00Z",
          timeRange: "09:00-10:00",
          capacity: 10,
          ownerId: 1,
        },
      ];
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(slotsBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001"
      );
      const response = await GET(request, { params: { facilityId: "facility-001" } });

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });

  describe("[S-01] POST 슬롯 등록 — happy path", () => {
    it("유효한 body이면 BE에 forward하고 201을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: 1,
        facilityId: "facility-001",
        date: "2026-06-01T09:00:00Z",
        timeRange: "09:00-10:00",
        capacity: 10,
        ownerId: 1,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001",
        {
          method: "POST",
          body: JSON.stringify({
            date: "2026-06-01T09:00:00Z",
            timeRange: "09:00-10:00",
            capacity: 10,
          }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { facilityId: "facility-001" } });

      expect(response.status).toBe(201);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });

  describe("POST 입력 검증 실패", () => {
    it("date 필드 누락 시 zod 거부 → 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001",
        {
          method: "POST",
          body: JSON.stringify({ timeRange: "09:00-10:00", capacity: 10 }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { facilityId: "facility-001" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("timeRange 필드 누락 시 zod 거부 → 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001",
        {
          method: "POST",
          body: JSON.stringify({ date: "2026-06-01T09:00:00Z", capacity: 10 }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { facilityId: "facility-001" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("capacity가 0이면 zod 거부 → 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001",
        {
          method: "POST",
          body: JSON.stringify({
            date: "2026-06-01T09:00:00Z",
            timeRange: "09:00-10:00",
            capacity: 0,
          }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { facilityId: "facility-001" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });

  describe("BE 401 응답 forward", () => {
    it("BE가 401을 반환하면 Route Handler도 401 + WWW-Authenticate 헤더를 반환한다", async () => {
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
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001"
      );
      const response = await GET(request, { params: { facilityId: "facility-001" } });

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
      const request = new NextRequest(
        "http://localhost:3000/api/portal/slots/facility-001"
      );
      const response = await GET(request, { params: { facilityId: "facility-001" } });

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });
  });
});

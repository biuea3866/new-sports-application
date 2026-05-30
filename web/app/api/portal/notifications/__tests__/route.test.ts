/**
 * BFF Route Handler 테스트 — Portal Notifications
 * GET  : 내 알림 목록 조회 forward
 *   - [S-01] happy path: 알림 목록 조회 → 200
 *   - onlyUnread 쿼리 파라미터 forward 확인
 * PATCH /[id]/read : 알림 읽음 처리 forward
 *   - [S-02] happy path: 읽음 처리 → 200
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

const baseNotification = {
  id: 1,
  userId: 10,
  channel: "IN_APP",
  templateId: "booking.confirmed",
  status: "SENT",
  sentAt: "2026-05-30T09:00:00Z",
  readAt: null,
  createdAt: "2026-05-30T09:00:00Z",
};

describe("Portal Notifications Route Handler — GET", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-01] GET 알림 목록 조회 — happy path", () => {
    it("알림 목록을 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const pageBody = {
        content: [baseNotification],
        totalElements: 1,
        totalPages: 1,
        page: 0,
        size: 20,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(pageBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/notifications?page=0&size=20"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const fetchCallArg = mockFetch.mock.calls[0]?.[0] as string;
      expect(fetchCallArg).toContain("/notifications/me");
    });

    it("onlyUnread=true 파라미터를 BE에 forward한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(
          JSON.stringify({
            content: [],
            totalElements: 0,
            totalPages: 0,
            page: 0,
            size: 20,
          }),
          { status: 200, headers: { "Content-Type": "application/json" } }
        )
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/notifications?onlyUnread=true"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      const fetchCallArg = mockFetch.mock.calls[0]?.[0] as string;
      expect(fetchCallArg).toContain("onlyUnread=true");
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
      const request = new NextRequest(
        "http://localhost:3000/api/portal/notifications"
      );
      const response = await GET(request);

      expect(response.status).toBe(401);
      expect(response.headers.get("WWW-Authenticate")).toBe(
        'Bearer realm="api"'
      );
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
        "http://localhost:3000/api/portal/notifications"
      );
      const response = await GET(request);

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe(
        "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
      );
    });
  });
});

describe("Portal Notifications Route Handler — PATCH /[id]/read", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-02] PATCH 알림 읽음 처리 — happy path", () => {
    it("읽음 처리 결과를 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const readNotification = {
        ...baseNotification,
        readAt: "2026-05-30T10:00:00Z",
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(readNotification), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PATCH } = await import("../[id]/read/route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/notifications/1/read",
        { method: "PATCH" }
      );
      const response = await PATCH(request, { params: { id: "1" } });

      expect(response.status).toBe(200);
      const fetchCallArg = mockFetch.mock.calls[0]?.[0] as string;
      expect(fetchCallArg).toContain("/notifications/1/read");
    });

    it("BE가 404를 반환하면 404를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(
          JSON.stringify({ title: "Not Found", status: 404 }),
          {
            status: 404,
            headers: { "Content-Type": "application/json" },
          }
        )
      );

      const { PATCH } = await import("../[id]/read/route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/notifications/999/read",
        { method: "PATCH" }
      );
      const response = await PATCH(request, { params: { id: "999" } });

      expect(response.status).toBe(404);
    });
  });
});

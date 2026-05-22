/**
 * BFF Route Handler 테스트 — Admin MCP Tokens
 * GET 목록 / POST 발급 입력 검증 / BE 401·5xx 동작
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Admin MCP Tokens Route Handler", () => {
  beforeEach(async () => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";

    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);
  });

  describe("GET 목록 조회", () => {
    it("[U-01] GET 요청을 BE /api/admin/mcp/tokens 에 forward하고 200을 반환한다", async () => {
      const listBody = {
        tokens: [
          {
            tokenId: 1,
            name: "테스트 토큰",
            status: "ACTIVE",
            expiresAt: null,
            lastUsedAt: null,
            createdAt: "2026-01-01T00:00:00Z",
          },
        ],
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(listBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const response = await GET();

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const url = (mockFetch.mock.calls[0] as [string])[0];
      expect(url).toContain("/api/admin/mcp/tokens");
    });
  });

  describe("POST 입력 검증", () => {
    it("[U-02] 유효한 body이면 BE에 forward하고 201을 반환한다", async () => {
      const beResponseBody = {
        tokenId: 1,
        name: "테스트 토큰",
        plainToken: "mcp_test_abc123",
        status: "ACTIVE",
        expiresAt: null,
        createdAt: "2026-01-01T00:00:00Z",
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/mcp/tokens", {
        method: "POST",
        body: JSON.stringify({
          name: "테스트 토큰",
          scopes: ["read:facility"],
          expiresAt: null,
        }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(201);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it("[U-03] name 누락 시 zod 거부 → 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/mcp/tokens", {
        method: "POST",
        body: JSON.stringify({ scopes: ["read:facility"], expiresAt: null }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("[U-04] scopes 빈 배열이면 zod 거부 → 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/mcp/tokens", {
        method: "POST",
        body: JSON.stringify({ name: "테스트 토큰", scopes: [], expiresAt: null }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("[U-05] scopes 누락 시 zod 거부 → 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/mcp/tokens", {
        method: "POST",
        body: JSON.stringify({ name: "테스트 토큰", expiresAt: null }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });

  describe("BE 오류 forward", () => {
    it("[U-06] BE 401 시 401 + WWW-Authenticate 헤더를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Unauthorized" }), {
          status: 401,
          headers: {
            "Content-Type": "application/json",
            "WWW-Authenticate": 'Bearer realm="api"',
          },
        })
      );

      const { GET } = await import("../route");
      const response = await GET();

      expect(response.status).toBe(401);
      expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });

    it("[U-07] BE 500 시 500 + 사용자 친화 메시지를 반환한다", async () => {
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Internal Server Error" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const response = await GET();

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });
  });
});

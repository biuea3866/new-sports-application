/**
 * BFF Route Handler 테스트 — 피처 플래그 상세/수정
 * GET 상세 조회, PUT 입력 검증 후 BE forward, BE 404 forward 동작
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("피처 플래그 상세/수정 Route Handler", () => {
  beforeEach(async () => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";

    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);
  });

  describe("GET 상세 조회", () => {
    it("key로 BE 상세 경로에 프록시되고 200 응답이 그대로 forward된다", async () => {
      const beResponseBody = {
        id: 1,
        key: "new-checkout-flow",
        type: "RELEASE",
        status: "ACTIVE",
        description: "신규 결제 플로우",
        strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-01T00:00:00Z",
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/feature-flags/new-checkout-flow"
      );
      const response = await GET(request, { params: { key: "new-checkout-flow" } });

      expect(response.status).toBe(200);
      const url = (mockFetch.mock.calls[0] as [string])[0];
      expect(url).toContain("/admin/feature-flags/new-checkout-flow");
    });

    it("BE가 404를 반환하면 404와 사용자 메시지가 forward된다", async () => {
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Not Found" }), {
          status: 404,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/feature-flags/unknown-flag"
      );
      const response = await GET(request, { params: { key: "unknown-flag" } });

      expect(response.status).toBe(404);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("요청한 리소스를 찾을 수 없습니다.");
    });
  });

  describe("PUT 수정", () => {
    const validBody = {
      description: "수정된 설명",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };

    it("유효한 body를 전달하면 BE PUT 경로로 프록시되고 200이 forward된다", async () => {
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ key: "new-checkout-flow", status: "ACTIVE" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PUT } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/feature-flags/new-checkout-flow",
        {
          method: "PUT",
          body: JSON.stringify(validBody),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await PUT(request, { params: { key: "new-checkout-flow" } });

      expect(response.status).toBe(200);
      const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
      expect(url).toContain("/admin/feature-flags/new-checkout-flow");
      expect(init.method).toBe("PUT");
    });

    it("description이 빈 문자열이면 zod 검증 400을 반환하고 BE를 호출하지 않는다", async () => {
      const invalidBody = {
        description: "",
        strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
      };

      const { PUT } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/feature-flags/new-checkout-flow",
        {
          method: "PUT",
          body: JSON.stringify(invalidBody),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await PUT(request, { params: { key: "new-checkout-flow" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });
});

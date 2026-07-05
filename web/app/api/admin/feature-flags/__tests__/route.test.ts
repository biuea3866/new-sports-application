/**
 * BFF Route Handler 테스트 — 피처 플래그 목록/생성
 * GET 쿼리 forward, POST 입력 검증 후 BE forward, BACKEND_URL 미설정 동작
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("피처 플래그 목록/생성 Route Handler", () => {
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
    it("쿼리 파라미터 없이 호출하면 BE 목록 경로로 그대로 forward한다", async () => {
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/feature-flags");
      const response = await GET(request);

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const url = (mockFetch.mock.calls[0] as [string])[0];
      expect(url).toContain("/admin/feature-flags");
      expect(url).not.toContain("?");
    });

    it("status=ARCHIVED 쿼리스트링이 BE 경로에 그대로 붙어 프록시된다", async () => {
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify([]), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/admin/feature-flags?status=ARCHIVED&type=RELEASE"
      );
      const response = await GET(request);

      expect(response.status).toBe(200);
      const url = (mockFetch.mock.calls[0] as [string])[0];
      expect(url).toContain("/admin/feature-flags?");
      expect(url).toContain("status=ARCHIVED");
      expect(url).toContain("type=RELEASE");
    });

    it("BACKEND_URL이 설정되지 않으면 503을 반환한다", async () => {
      delete process.env["BACKEND_URL"];

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/feature-flags");
      const response = await GET(request);

      expect(response.status).toBe(503);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });

  describe("POST 생성", () => {
    const validBody = {
      key: "new-checkout-flow",
      type: "RELEASE",
      description: "신규 결제 플로우",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false },
    };

    it("유효한 body를 전달하면 BE로 프록시되고 BE의 201 응답이 그대로 forward된다", async () => {
      const beResponseBody = {
        id: 1,
        key: "new-checkout-flow",
        type: "RELEASE",
        status: "ACTIVE",
        description: "신규 결제 플로우",
        strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false },
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
      const request = new NextRequest("http://localhost:3000/api/admin/feature-flags", {
        method: "POST",
        body: JSON.stringify(validBody),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(201);
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
      expect(url).toContain("/admin/feature-flags");
      expect(init.method).toBe("POST");
      const body = await response.json();
      expect(body).toEqual(beResponseBody);
    });

    it("percentage가 100을 초과하면 zod 검증 400을 반환하고 BE를 호출하지 않는다", async () => {
      const invalidBody = {
        key: "over-percentage-flag",
        type: "EXPERIMENT",
        description: "비율 초과 케이스",
        strategy: { strategyType: "PERCENTAGE_ROLLOUT", percentage: 150 },
      };

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/feature-flags", {
        method: "POST",
        body: JSON.stringify(invalidBody),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("깨진 JSON body를 전달하면 400을 반환하고 BE를 호출하지 않는다", async () => {
      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/admin/feature-flags", {
        method: "POST",
        body: "{ 잘못된 JSON",
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });
});

/**
 * BFF Route Handler 테스트 — 시설 단건(PATCH/GET/DELETE)
 * [FE-04] PATCH 요청에서도 sido가 통과된다
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Facility Detail Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("PATCH sido payload 전달", () => {
    it("PATCH 요청 본문에 sido가 있으면 BE payload에 sido가 포함된다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ id: "fac-001" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PATCH } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities/fac-001", {
        method: "PATCH",
        body: JSON.stringify({ sido: "26" }),
        headers: { "Content-Type": "application/json" },
      });
      await PATCH(request, { params: { id: "fac-001" } });

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
      const sentBody = JSON.parse(fetchInit.body as string) as Record<string, unknown>;
      expect(sentBody["sido"]).toBe("26");
    });

    it("sido 없이 다른 필드만 수정해도 회귀 없이 동작한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ id: "fac-001" }), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PATCH } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities/fac-001", {
        method: "PATCH",
        body: JSON.stringify({ name: "새 이름" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await PATCH(request, { params: { id: "fac-001" } });

      expect(response.status).toBe(200);
      const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
      const sentBody = JSON.parse(fetchInit.body as string) as Record<string, unknown>;
      expect("sido" in sentBody).toBe(false);
      expect(sentBody["name"]).toBe("새 이름");
    });

    it("빈 본문(수정 필드 0개)이면 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { PATCH } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities/fac-001", {
        method: "PATCH",
        body: JSON.stringify({}),
        headers: { "Content-Type": "application/json" },
      });
      const response = await PATCH(request, { params: { id: "fac-001" } });

      expect(response.status).toBe(400);
    });
  });
});

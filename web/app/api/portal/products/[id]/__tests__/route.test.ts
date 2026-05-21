/**
 * BFF Route Handler 테스트 — Products [id]
 * PATCH: UpdateProductInputSchema 검증
 *   - happy path → 200
 *   - 빈 객체 입력 → 400 (refine 최소 1 필드)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Products [id] Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("PATCH 입력 검증 (UpdateProductInputSchema)", () => {
    it("[happy path] 유효한 부분 업데이트 body이면 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: 1,
        name: "프리미엄 스포츠 양말",
        description: "고품질 스포츠 양말",
        price: 9900,
        status: "ACTIVE",
        stockQuantity: 100,
        ownerId: 1,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-02T00:00:00Z",
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PATCH } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/products/1", {
        method: "PATCH",
        body: JSON.stringify({ name: "프리미엄 스포츠 양말" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await PATCH(request, { params: { id: "1" } });

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it("빈 객체 입력 시 refine 거부 → 400을 반환한다 (최소 1 필드 필요)", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { PATCH } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/products/1", {
        method: "PATCH",
        body: JSON.stringify({}),
        headers: { "Content-Type": "application/json" },
      });
      const response = await PATCH(request, { params: { id: "1" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("price만 업데이트하는 body이면 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: 1,
        name: "스포츠 양말",
        description: "고품질 스포츠 양말",
        price: 12000,
        status: "ACTIVE",
        stockQuantity: 100,
        ownerId: 1,
        createdAt: "2026-01-01T00:00:00Z",
        updatedAt: "2026-01-02T00:00:00Z",
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { PATCH } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/products/1", {
        method: "PATCH",
        body: JSON.stringify({ price: 12000 }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await PATCH(request, { params: { id: "1" } });

      expect(response.status).toBe(200);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });
});

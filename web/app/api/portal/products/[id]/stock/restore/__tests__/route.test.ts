/**
 * BFF Route Handler 테스트 — Products [id] stock restore
 * POST: RestoreStockInputSchema 검증
 *   - happy path → 200
 *   - quantity = 0 → 400 (positive)
 *   - quantity = -1 → 400
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Products [id] Stock Restore Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("POST 입력 검증 (RestoreStockInputSchema)", () => {
    it("[happy path] 유효한 quantity이면 BE에 forward하고 200을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(null, { status: 204 })
      );

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/products/1/stock/restore",
        {
          method: "POST",
          body: JSON.stringify({ quantity: 10 }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(204);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });

    it("quantity = 0 이면 zod 거부 → 400을 반환한다 (positive 조건)", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/products/1/stock/restore",
        {
          method: "POST",
          body: JSON.stringify({ quantity: 0 }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("quantity = -1 이면 zod 거부 → 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/products/1/stock/restore",
        {
          method: "POST",
          body: JSON.stringify({ quantity: -1 }),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });

    it("quantity 필드 누락 시 zod 거부 → 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest(
        "http://localhost:3000/api/portal/products/1/stock/restore",
        {
          method: "POST",
          body: JSON.stringify({}),
          headers: { "Content-Type": "application/json" },
        }
      );
      const response = await POST(request, { params: { id: "1" } });

      expect(response.status).toBe(400);
      expect(mockFetch).not.toHaveBeenCalled();
    });
  });
});

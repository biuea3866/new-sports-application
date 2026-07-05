/**
 * BFF Route Handler 테스트 — 피처 플래그 감사 로그 이력
 * GET 쿼리(page, size) forward, BACKEND_URL 미설정 동작
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("피처 플래그 감사 로그 Route Handler", () => {
  beforeEach(async () => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";

    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);
  });

  it("page, size 쿼리스트링이 BE 감사 로그 경로에 그대로 붙어 프록시된다", async () => {
    const beResponseBody = {
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageNumber: 0,
      pageSize: 20,
    };
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify(beResponseBody), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { GET } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/admin/feature-flags/new-checkout-flow/audit-logs?page=1&size=20"
    );
    const response = await GET(request, { params: { key: "new-checkout-flow" } });

    expect(response.status).toBe(200);
    const url = (mockFetch.mock.calls[0] as [string])[0];
    expect(url).toContain("/admin/feature-flags/new-checkout-flow/audit-logs?");
    expect(url).toContain("page=1");
    expect(url).toContain("size=20");
  });

  it("쿼리 파라미터 없이 호출해도 BE 감사 로그 경로로 forward한다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ content: [], totalElements: 0, totalPages: 0, pageNumber: 0, pageSize: 20 }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { GET } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/admin/feature-flags/new-checkout-flow/audit-logs"
    );
    const response = await GET(request, { params: { key: "new-checkout-flow" } });

    expect(response.status).toBe(200);
    const url = (mockFetch.mock.calls[0] as [string])[0];
    expect(url).toContain("/admin/feature-flags/new-checkout-flow/audit-logs");
    expect(url).not.toContain("?");
  });

  it("BACKEND_URL이 설정되지 않으면 503을 반환한다", async () => {
    delete process.env["BACKEND_URL"];

    const { GET } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/admin/feature-flags/new-checkout-flow/audit-logs"
    );
    const response = await GET(request, { params: { key: "new-checkout-flow" } });

    expect(response.status).toBe(503);
    expect(mockFetch).not.toHaveBeenCalled();
  });
});

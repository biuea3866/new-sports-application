/**
 * BFF Route Handler 테스트 — 피처 플래그 재활성화
 * POST forward, BE 404 forward 동작
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("피처 플래그 재활성화 Route Handler", () => {
  beforeEach(async () => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";

    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);
  });

  it("BE 재활성화 경로로 프록시되고 200 응답이 그대로 forward된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ key: "new-checkout-flow", status: "ACTIVE" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { POST } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/admin/feature-flags/new-checkout-flow/activate",
      { method: "POST" }
    );
    const response = await POST(request, { params: { key: "new-checkout-flow" } });

    expect(response.status).toBe(200);
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/admin/feature-flags/new-checkout-flow/activate");
    expect(init.method).toBe("POST");
  });

  it("BE가 존재하지 않는 key라 404를 반환하면 404와 사용자 메시지가 forward된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ title: "Not Found" }), {
        status: 404,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { POST } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/admin/feature-flags/unknown-flag/activate",
      { method: "POST" }
    );
    const response = await POST(request, { params: { key: "unknown-flag" } });

    expect(response.status).toBe(404);
    const body = (await response.json()) as { message: string };
    expect(body.message).toBe("요청한 리소스를 찾을 수 없습니다.");
  });
});

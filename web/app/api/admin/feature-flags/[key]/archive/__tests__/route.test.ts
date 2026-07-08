/**
 * BFF Route Handler 테스트 — 피처 플래그 아카이브
 * POST forward, BE 409(이미 ARCHIVED) forward 동작
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("피처 플래그 아카이브 Route Handler", () => {
  beforeEach(async () => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";

    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue({
      get: vi.fn().mockReturnValue({ value: "test-token" }),
    } as unknown as ReturnType<typeof cookies>);
  });

  it("BE 아카이브 경로로 프록시되고 200 응답이 그대로 forward된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ key: "new-checkout-flow", status: "ARCHIVED" }), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { POST } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/admin/feature-flags/new-checkout-flow/archive",
      { method: "POST" }
    );
    const response = await POST(request, { params: { key: "new-checkout-flow" } });

    expect(response.status).toBe(200);
    const [url, init] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(url).toContain("/admin/feature-flags/new-checkout-flow/archive");
    expect(init.method).toBe("POST");
  });

  it("BE가 이미 ARCHIVED 상태라 409를 반환하면 409와 사용자 메시지가 forward된다", async () => {
    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ title: "Conflict" }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { POST } = await import("../route");
    const request = new NextRequest(
      "http://localhost:3000/api/admin/feature-flags/new-checkout-flow/archive",
      { method: "POST" }
    );
    const response = await POST(request, { params: { key: "new-checkout-flow" } });

    expect(response.status).toBe(409);
    const body = (await response.json()) as { message: string };
    expect(body.message).toBe("이미 존재하거나 충돌이 발생했습니다.");
  });
});

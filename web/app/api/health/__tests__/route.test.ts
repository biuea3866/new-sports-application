/**
 * /api/health Route Handler 통합 테스트
 * S-02: GET /api/health가 web/be 두 상태를 합산해 200을 반환한다 (BE mock)
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("/api/health Route Handler", () => {
  const originalBackendUrl = process.env["BACKEND_URL"];

  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
  });

  afterEach(() => {
    if (originalBackendUrl !== undefined) {
      process.env["BACKEND_URL"] = originalBackendUrl;
    } else {
      delete process.env["BACKEND_URL"];
    }
  });

  it("S-02a: BE가 UP 응답하면 { web: ok, be: ok } 200을 반환한다", async () => {
    process.env["BACKEND_URL"] = "http://localhost:8080";

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ status: "UP" }), { status: 200 })
    );

    const { GET } = await import("@/app/api/health/route");
    const response = await GET();
    const body = await response.json() as { web: string; be: string; timestamp: string };

    expect(response.status).toBe(200);
    expect(body.web).toBe("ok");
    expect(body.be).toBe("ok");
    expect(body.timestamp).toBeDefined();
  });

  it("S-02b: BE가 DOWN 응답하면 { web: ok, be: error } 200을 반환한다", async () => {
    process.env["BACKEND_URL"] = "http://localhost:8080";

    mockFetch.mockResolvedValue(
      new Response(JSON.stringify({ status: "DOWN" }), { status: 200 })
    );

    const { GET } = await import("@/app/api/health/route");
    const response = await GET();
    const body = await response.json() as { web: string; be: string };

    expect(response.status).toBe(200);
    expect(body.web).toBe("ok");
    expect(body.be).toBe("error");
  });

  it("S-02c: BE fetch가 실패하면 { web: ok, be: unavailable } 200을 반환한다", async () => {
    process.env["BACKEND_URL"] = "http://localhost:8080";

    mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));

    const { GET } = await import("@/app/api/health/route");
    const response = await GET();
    const body = await response.json() as { web: string; be: string };

    expect(response.status).toBe(200);
    expect(body.web).toBe("ok");
    expect(body.be).toBe("unavailable");
  });

  it("S-02d: BE 응답이 non-OK HTTP 상태면 { web: ok, be: error } 200을 반환한다", async () => {
    process.env["BACKEND_URL"] = "http://localhost:8080";

    mockFetch.mockResolvedValue(
      new Response(null, { status: 503 })
    );

    const { GET } = await import("@/app/api/health/route");
    const response = await GET();
    const body = await response.json() as { web: string; be: string };

    expect(response.status).toBe(200);
    expect(body.web).toBe("ok");
    expect(body.be).toBe("error");
  });

  it("S-02e: BACKEND_URL이 없으면 { web: ok, be: unavailable } 200을 반환한다", async () => {
    delete process.env["BACKEND_URL"];

    const { GET } = await import("@/app/api/health/route");
    const response = await GET();
    const body = await response.json() as { web: string; be: string };

    expect(response.status).toBe(200);
    expect(body.web).toBe("ok");
    expect(body.be).toBe("unavailable");
  });
});

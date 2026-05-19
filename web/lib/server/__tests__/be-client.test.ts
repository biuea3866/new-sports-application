/**
 * be-client.ts 단위 테스트
 * U-01: JWT 쿠키 부재 시 요청에 Authorization 헤더가 포함되지 않는다
 * U-02: BACKEND_URL 환경 변수 누락 시 부팅 단계에서 명확한 에러를 던진다
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// next/headers의 cookies()를 모킹한다
vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

// fetch를 전역 모킹한다
const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("be-client", () => {
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

  describe("U-01: JWT 쿠키 부재 시 Authorization 헤더 미부착", () => {
    it("access_token 쿠키가 없으면 Authorization 헤더 없이 fetch를 호출한다", async () => {
      process.env["BACKEND_URL"] = "http://localhost:8080";

      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));

      const { beClient } = await import("@/lib/server/be-client");
      await beClient("/test");

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit & { headers: Record<string, string> }];
      expect(fetchInit.headers).not.toHaveProperty("Authorization");
    });
  });

  describe("U-01b: JWT 쿠키 존재 시 Authorization 헤더 부착", () => {
    it("access_token 쿠키가 있으면 Authorization: Bearer <token> 헤더를 부착한다", async () => {
      process.env["BACKEND_URL"] = "http://localhost:8080";

      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-jwt-token" }),
      } as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));

      const { beClient } = await import("@/lib/server/be-client");
      await beClient("/test");

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit & { headers: Record<string, string> }];
      expect(fetchInit.headers).toHaveProperty("Authorization", "Bearer test-jwt-token");
    });
  });

  describe("U-02: BACKEND_URL 환경 변수 누락 시 에러", () => {
    it("BACKEND_URL이 undefined이면 모듈 로드 시 Error를 던진다", async () => {
      delete process.env["BACKEND_URL"];

      await expect(import("@/lib/server/be-client")).rejects.toThrow(
        "BACKEND_URL 환경 변수가 설정되지 않았습니다"
      );
    });
  });
});

/**
 * BFF /api/auth/login Route Handler 테스트
 *
 * U-01: 유효한 자격증명으로 POST하면 BE를 호출하고 access_token 쿠키를 set한다
 * U-02: 잘못된 자격증명으로 POST하면 BE 401을 그대로 401로 반환한다
 * U-03: email 또는 password 누락 시 zod 검증 실패로 400을 반환한다
 * U-04: BE 연결 실패(네트워크 오류) 시 503을 반환한다
 * U-05: BE 5xx 응답 시 500을 반환한다
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

const mockCookiesSet = vi.fn();
const mockCookiesGet = vi.fn();

vi.mock("next/headers", () => ({
  cookies: () => ({
    get: mockCookiesGet,
    set: mockCookiesSet,
  }),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("/api/auth/login Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    mockCookiesSet.mockReset();
    mockCookiesGet.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[U-01] 유효한 자격증명 → BE 호출 + access_token 쿠키 set + refreshToken 쿠키 set", () => {
    it("BE가 200과 토큰을 반환하면 access_token 쿠키를 set하고 200을 반환한다", async () => {
      const beResponseBody = {
        accessToken: "eyJ.payload.sig",
        refreshToken: "refresh.token.value",
        accessTokenExpiresIn: 3600,
      };
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify(beResponseBody), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: "owner@example.com", password: "correct-password" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      // BE를 호출했는지 검증
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [calledUrl] = mockFetch.mock.calls[0] as [string, unknown];
      expect(calledUrl).toContain("/auth/login");

      // access_token 쿠키가 set됐는지 검증
      expect(mockCookiesSet).toHaveBeenCalledWith(
        "access_token",
        "eyJ.payload.sig",
        expect.objectContaining({ httpOnly: true })
      );

      // refresh_token 쿠키가 set됐는지 검증
      expect(mockCookiesSet).toHaveBeenCalledWith(
        "refresh_token",
        "refresh.token.value",
        expect.objectContaining({ httpOnly: true })
      );

      expect(response.status).toBe(200);
    });
  });

  describe("[U-02] 잘못된 자격증명 → BE 401을 401로 반환", () => {
    it("BE가 401을 반환하면 쿠키를 set하지 않고 401을 반환한다", async () => {
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ detail: "이메일 또는 비밀번호가 올바르지 않습니다." }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: "owner@example.com", password: "wrong-password" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      // 쿠키 set 금지
      expect(mockCookiesSet).not.toHaveBeenCalled();
      expect(response.status).toBe(401);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });
  });

  describe("[U-03] 입력 검증 실패 → 400 반환", () => {
    it("email이 누락되면 BE를 호출하지 않고 400을 반환한다", async () => {
      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ password: "some-password" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(mockFetch).not.toHaveBeenCalled();
      expect(response.status).toBe(400);
    });

    it("password가 누락되면 BE를 호출하지 않고 400을 반환한다", async () => {
      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: "owner@example.com" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(mockFetch).not.toHaveBeenCalled();
      expect(response.status).toBe(400);
    });

    it("빈 email이면 BE를 호출하지 않고 400을 반환한다", async () => {
      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: "", password: "some-password" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(mockFetch).not.toHaveBeenCalled();
      expect(response.status).toBe(400);
    });
  });

  describe("[U-04] 네트워크 오류 → 503 반환", () => {
    it("BE 연결 실패 시 쿠키를 set하지 않고 503을 반환한다", async () => {
      mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: "owner@example.com", password: "password" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(mockCookiesSet).not.toHaveBeenCalled();
      expect(response.status).toBe(503);
    });
  });

  describe("[U-05] BE 5xx 응답 → 500 반환", () => {
    it("BE가 500을 반환하면 쿠키를 set하지 않고 500을 반환한다", async () => {
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Internal Server Error" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ email: "owner@example.com", password: "password" }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(mockCookiesSet).not.toHaveBeenCalled();
      expect(response.status).toBe(500);
    });
  });
});

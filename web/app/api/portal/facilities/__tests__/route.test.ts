/**
 * BFF Route Handler 테스트
 * S-01: Route Handler가 BE 401 응답을 401 + WWW-Authenticate 헤더로 forward
 * S-02: BE 5xx 응답 시 사용자 친화 메시지 반환
 * U-02: 401 응답 시 로그인 페이지 redirect 트리거 (401 응답 반환 검증)
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { NextRequest } from "next/server";

// next/headers의 cookies()를 모킹한다
vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

// server-only 모킹은 vitest setup에서 처리됨

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

describe("Portal Facilities Route Handler", () => {
  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  describe("[S-01] BE 401 응답을 401 + WWW-Authenticate 헤더로 forward", () => {
    it("BE가 401을 반환하면 Route Handler도 401 + WWW-Authenticate 헤더를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      const beHeaders = new Headers({
        "Content-Type": "application/json",
        "WWW-Authenticate": 'Bearer realm="api"',
      });
      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), {
          status: 401,
          headers: beHeaders,
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(401);
      expect(response.headers.get("WWW-Authenticate")).toBe('Bearer realm="api"');
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });

    it("BE 401에 WWW-Authenticate 헤더가 없어도 401을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Unauthorized", status: 401 }), {
          status: 401,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(401);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("로그인이 필요합니다.");
    });
  });

  describe("[S-02] BE 5xx 응답 시 사용자 친화 메시지 반환", () => {
    it("BE가 500을 반환하면 사용자 친화 메시지와 500을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Internal Server Error" }), {
          status: 500,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(500);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
    });

    it("BE가 503을 반환해도 500 + 사용자 친화 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response("Service Unavailable", {
          status: 503,
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(500);
    });
  });

  describe("BE 4xx 에러 매핑", () => {
    it("BE 403 응답 시 403 + 권한 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Forbidden", status: 403 }), {
          status: 403,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(403);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("해당 작업을 수행할 권한이 없습니다.");
    });

    it("BE 404 응답 시 404 + 리소스 없음 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Not Found", status: 404 }), {
          status: 404,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(404);
      const body = (await response.json()) as { message: string };
      expect(body.message).toBe("요청한 리소스를 찾을 수 없습니다.");
    });

    it("BE 409 응답 시 409 + 충돌 메시지를 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ title: "Conflict", status: 409, detail: "활성 슬롯이 있습니다." }), {
          status: 409,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(409);
      const body = (await response.json()) as { message: string; detail?: string };
      expect(body.message).toBe("이미 존재하거나 충돌이 발생했습니다.");
      expect(body.detail).toBe("활성 슬롯이 있습니다.");
    });
  });

  describe("zod 입력 검증 (POST)", () => {
    it("필수 필드 누락 시 400을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities", {
        method: "POST",
        body: JSON.stringify({ name: "시설" }), // code, gu, type, address, location, parking, tel, eduYn 누락
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(400);
    });

    it("유효한 body이면 BE에 forward한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      const beResponseBody = {
        id: "fac-001",
        code: "GN-01",
        name: "강남 풋살장",
        gu: "강남구",
        type: "INDOOR",
        address: "서울특별시 강남구",
        location: "37.5,127.0",
        parking: true,
        tel: "02-1234-5678",
        homePage: null,
        eduYn: false,
        meta: null,
        ownerUserId: 1,
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
      const request = new NextRequest("http://localhost:3000/api/portal/facilities", {
        method: "POST",
        body: JSON.stringify({
          code: "GN-01",
          name: "강남 풋살장",
          gu: "강남구",
          type: "INDOOR",
          address: "서울특별시 강남구",
          location: "37.5,127.0",
          parking: true,
          tel: "02-1234-5678",
          eduYn: false,
        }),
        headers: { "Content-Type": "application/json" },
      });
      const response = await POST(request);

      expect(response.status).toBe(201);
      expect(mockFetch).toHaveBeenCalledTimes(1);
    });
  });

  describe("sido payload 전달", () => {
    it("POST 요청 본문에 sido가 있으면 BE payload에 sido가 포함된다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ id: "fac-002" }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities", {
        method: "POST",
        body: JSON.stringify({
          code: "BS-01",
          name: "해운대 풋살장",
          gu: "해운대구",
          sido: "26",
          type: "OUTDOOR",
          address: "부산광역시 해운대구",
          location: "35.16,129.16",
          parking: false,
          tel: "051-1234-5678",
          eduYn: false,
        }),
        headers: { "Content-Type": "application/json" },
      });
      await POST(request);

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
      const sentBody = JSON.parse(fetchInit.body as string) as Record<string, unknown>;
      expect(sentBody["sido"]).toBe("26");
    });

    it("sido 미입력(undefined) 시 payload에 sido 없이 전달되어 서버 보간에 맡긴다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ id: "fac-001" }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities", {
        method: "POST",
        body: JSON.stringify({
          code: "GN-01",
          name: "강남 풋살장",
          gu: "강남구",
          type: "INDOOR",
          address: "서울특별시 강남구",
          location: "37.5,127.0",
          parking: true,
          tel: "02-1234-5678",
          eduYn: false,
        }),
        headers: { "Content-Type": "application/json" },
      });
      await POST(request);

      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
      const sentBody = JSON.parse(fetchInit.body as string) as Record<string, unknown>;
      expect("sido" in sentBody).toBe(false);
    });

    it("기존 location \"lat,lng\" → lat/lng number 분해가 회귀 없이 동작한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue({ value: "test-token" }),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockResolvedValue(
        new Response(JSON.stringify({ id: "fac-003" }), {
          status: 201,
          headers: { "Content-Type": "application/json" },
        })
      );

      const { POST } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities", {
        method: "POST",
        body: JSON.stringify({
          code: "GN-02",
          name: "강남 풋살장 2",
          gu: "강남구",
          sido: "11",
          type: "INDOOR",
          address: "서울특별시 강남구",
          location: "37.5123, 127.0456",
          parking: true,
          tel: "02-1234-5678",
          eduYn: false,
        }),
        headers: { "Content-Type": "application/json" },
      });
      await POST(request);

      const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
      const sentBody = JSON.parse(fetchInit.body as string) as Record<string, unknown>;
      expect(sentBody["lat"]).toBe(37.5123);
      expect(sentBody["lng"]).toBe(127.0456);
      expect(sentBody["sido"]).toBe("11");
    });
  });

  describe("네트워크 오류 처리", () => {
    it("BE 연결 실패 시 503을 반환한다", async () => {
      const { cookies } = await import("next/headers");
      vi.mocked(cookies).mockReturnValue({
        get: vi.fn().mockReturnValue(undefined),
      } as unknown as ReturnType<typeof cookies>);

      mockFetch.mockRejectedValue(new Error("ECONNREFUSED"));

      const { GET } = await import("../route");
      const request = new NextRequest("http://localhost:3000/api/portal/facilities");
      const response = await GET(request);

      expect(response.status).toBe(503);
    });
  });
});

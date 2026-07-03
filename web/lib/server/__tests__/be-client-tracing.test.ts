/**
 * be-client.ts 의 traceparent(W3C Trace Context) 전파 검증 (FE-02).
 *
 * 실제 @vercel/otel(mock 하지 않음)을 사용한다 — registerOTel 등록 전/후의 global fetch
 * 동작 차이를 비교해 "계측 off 상태(회귀 기준선)"와 "계측 on 상태"를 함께 검증한다.
 * 이 파일 안에서만 registerOTel 을 등록해 다른 테스트 파일(be-client.test.ts 등)의
 * global fetch 동작에 영향을 주지 않는다(vitest 파일 단위 isolate).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("next/headers", () => ({
  cookies: vi.fn(),
}));

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function stubCookies(token: string | undefined): { get: ReturnType<typeof vi.fn> } {
  return {
    get: vi.fn().mockReturnValue(token === undefined ? undefined : { value: token }),
  };
}

/** @vercel/otel 계측이 적용되면 headers 가 네이티브 Headers 인스턴스로 정규화된다. */
function extractHeaderValue(headers: unknown, key: string): string | null | undefined {
  if (headers instanceof Headers) {
    return headers.get(key);
  }
  return (headers as Record<string, string> | undefined)?.[key];
}

const TRACEPARENT_PATTERN = /^00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]$/;

describe("be-client traceparent 전파", () => {
  const originalBackendUrl = process.env["BACKEND_URL"];

  beforeEach(() => {
    vi.resetModules();
    mockFetch.mockReset();
    mockFetch.mockResolvedValue(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    process.env["BACKEND_URL"] = "http://localhost:8080";
  });

  afterEach(() => {
    if (originalBackendUrl !== undefined) {
      process.env["BACKEND_URL"] = originalBackendUrl;
    } else {
      delete process.env["BACKEND_URL"];
    }
  });

  it("OTel 계측 등록 전에는 beClient 요청 헤더에 traceparent가 없다(회귀 기준선)", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue(stubCookies(undefined) as unknown as ReturnType<typeof cookies>);

    const { beClient } = await import("@/lib/server/be-client");
    await beClient("/test");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(extractHeaderValue(fetchInit.headers, "traceparent")).toBeFalsy();
  });

  it("OTel 계측이 등록되면(registerOTel) beClient 요청 헤더에 traceparent가 자동으로 실린다", async () => {
    const { registerOTel } = await import("@vercel/otel");
    registerOTel({
      serviceName: "be-client-tracing-test",
      attributes: { "deployment.environment": "test" },
    });

    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue(stubCookies(undefined) as unknown as ReturnType<typeof cookies>);

    const { beClient } = await import("@/lib/server/be-client");
    await beClient("/test");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(extractHeaderValue(fetchInit.headers, "traceparent")).toMatch(TRACEPARENT_PATTERN);
  });

  it("OTLP export 미설정 상태(no-op)에서도 beClient 요청이 정상 성공한다", async () => {
    delete process.env["OTEL_EXPORTER_OTLP_ENDPOINT"];

    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue(stubCookies(undefined) as unknown as ReturnType<typeof cookies>);

    const { beClient } = await import("@/lib/server/be-client");
    const response = await beClient("/test");

    expect(response.ok).toBe(true);
  });

  it("계측 등록 후에도 access_token 쿠키가 있으면 Authorization 헤더가 traceparent와 함께 유지된다(회귀 없음)", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue(stubCookies("test-jwt-token") as unknown as ReturnType<typeof cookies>);

    const { beClient } = await import("@/lib/server/be-client");
    await beClient("/test");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(extractHeaderValue(fetchInit.headers, "Authorization")).toBe("Bearer test-jwt-token");
    expect(extractHeaderValue(fetchInit.headers, "traceparent")).toMatch(TRACEPARENT_PATTERN);
  });

  it("계측 등록 후에도 timeoutMs 미지정 시 기본 AbortSignal이 부착된다(timeout 동작 회귀 없음)", async () => {
    const { cookies } = await import("next/headers");
    vi.mocked(cookies).mockReturnValue(stubCookies(undefined) as unknown as ReturnType<typeof cookies>);

    const { beClient } = await import("@/lib/server/be-client");
    await beClient("/test");

    expect(mockFetch).toHaveBeenCalledTimes(1);
    const [, fetchInit] = mockFetch.mock.calls[0] as [string, RequestInit];
    expect(fetchInit.signal).toBeInstanceOf(AbortSignal);
  });
});

/**
 * instrumentation.ts 단위 테스트
 * FE-01: register() 가 env → resource config 를 산출해 registerOTel 을 호출한다.
 *        registerOTel 이 실패해도 register() 는 예외를 전파하지 않는다(장애 격리).
 *
 * @vercel/otel 은 mock 한다 — 실제 SDK 등록(전역 트레이서 프로바이더 등록)은
 * be-client.test.ts 의 traceparent 전파 테스트에서 실제 패키지로 별도 검증한다.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

const registerOTelMock = vi.fn();
vi.mock("@vercel/otel", () => ({
  registerOTel: registerOTelMock,
}));

describe("instrumentation", () => {
  const originalAppEnv = process.env["APP_ENV"];
  const originalServiceName = process.env["OTEL_SERVICE_NAME"];

  beforeEach(() => {
    vi.resetModules();
    registerOTelMock.mockReset();
  });

  afterEach(() => {
    if (originalAppEnv !== undefined) {
      process.env["APP_ENV"] = originalAppEnv;
    } else {
      delete process.env["APP_ENV"];
    }
    if (originalServiceName !== undefined) {
      process.env["OTEL_SERVICE_NAME"] = originalServiceName;
    } else {
      delete process.env["OTEL_SERVICE_NAME"];
    }
  });

  it("APP_ENV=dev 이면 deployment.environment=dev 로 registerOTel 을 호출한다", async () => {
    process.env["APP_ENV"] = "dev";
    process.env["OTEL_SERVICE_NAME"] = "sports-web";

    const { register } = await import("../instrumentation");
    register();

    expect(registerOTelMock).toHaveBeenCalledTimes(1);
    expect(registerOTelMock).toHaveBeenCalledWith({
      serviceName: "sports-web",
      attributes: { "deployment.environment": "dev" },
    });
  });

  it("APP_ENV 미설정 시 기본값(local/sports-web)으로 registerOTel 을 호출한다", async () => {
    delete process.env["APP_ENV"];
    delete process.env["OTEL_SERVICE_NAME"];

    const { register } = await import("../instrumentation");
    register();

    expect(registerOTelMock).toHaveBeenCalledWith({
      serviceName: "sports-web",
      attributes: { "deployment.environment": "local" },
    });
  });

  it("registerOTel 이 예외를 던져도 register() 는 예외 없이 완료된다(OTLP 미설정·오설정에도 앱 정상 부팅)", async () => {
    registerOTelMock.mockImplementation(() => {
      throw new Error("OTLP endpoint unreachable");
    });

    const { register } = await import("../instrumentation");

    expect(() => register()).not.toThrow();
  });
});

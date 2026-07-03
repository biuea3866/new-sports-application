/**
 * otel-resource.ts 단위 테스트
 * FE-01: APP_ENV → deployment.environment 매핑, service.name 산출 (순수 함수)
 */
import { describe, it, expect } from "vitest";
import {
  resolveDeploymentEnvironment,
  resolveServiceName,
  resolveOtelResourceConfig,
} from "@/lib/server/otel-resource";

describe("otel-resource", () => {
  describe("resolveDeploymentEnvironment", () => {
    it("APP_ENV=dev 이면 deployment.environment=dev 를 반환한다", () => {
      expect(resolveDeploymentEnvironment("dev")).toBe("dev");
    });

    it("APP_ENV 가 미설정(undefined)이면 기본값 local 을 반환한다", () => {
      expect(resolveDeploymentEnvironment(undefined)).toBe("local");
    });

    it("APP_ENV 가 빈 문자열이면 기본값 local 을 반환한다", () => {
      expect(resolveDeploymentEnvironment("")).toBe("local");
    });
  });

  describe("resolveServiceName", () => {
    it("OTEL_SERVICE_NAME 이 지정되면 그 값을 그대로 반환한다", () => {
      expect(resolveServiceName("custom-service")).toBe("custom-service");
    });

    it("OTEL_SERVICE_NAME 이 미설정이면 기본값 sports-web 을 반환한다", () => {
      expect(resolveServiceName(undefined)).toBe("sports-web");
    });
  });

  describe("resolveOtelResourceConfig", () => {
    it("env 객체로부터 serviceName·deployment.environment 를 함께 산출한다", () => {
      const config = resolveOtelResourceConfig({
        OTEL_SERVICE_NAME: "sports-web",
        APP_ENV: "prod",
      });

      expect(config).toEqual({
        serviceName: "sports-web",
        attributes: {
          "deployment.environment": "prod",
        },
      });
    });

    it("env 값이 모두 없으면 기본값(sports-web/local)으로 산출한다", () => {
      const config = resolveOtelResourceConfig({});

      expect(config).toEqual({
        serviceName: "sports-web",
        attributes: {
          "deployment.environment": "local",
        },
      });
    });
  });
});

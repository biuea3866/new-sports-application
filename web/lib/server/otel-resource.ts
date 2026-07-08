/**
 * OTel resource attribute 산출 (순수 함수).
 * BE TDD env 태그 규약(`deployment.environment` = `APP_ENV`, 신규 키 없음)과 동일 값 체계를 사용한다.
 * 근거: design-fe-web.md "계측 config 계약".
 */

const DEFAULT_SERVICE_NAME = "sports-web";
const DEFAULT_DEPLOYMENT_ENVIRONMENT = "local";

function isNonEmpty(value: string | undefined): value is string {
  return value !== undefined && value.trim().length > 0;
}

/** APP_ENV → deployment.environment 매핑. 미설정·빈 문자열이면 `local` 기본값. */
export function resolveDeploymentEnvironment(appEnv: string | undefined): string {
  return isNonEmpty(appEnv) ? appEnv : DEFAULT_DEPLOYMENT_ENVIRONMENT;
}

/** OTEL_SERVICE_NAME → service.name 산출. 미설정·빈 문자열이면 `sports-web` 기본값(BE `sports-application`과 구분). */
export function resolveServiceName(otelServiceName: string | undefined): string {
  return isNonEmpty(otelServiceName) ? otelServiceName : DEFAULT_SERVICE_NAME;
}

export interface OtelResourceEnv {
  OTEL_SERVICE_NAME?: string;
  APP_ENV?: string;
}

export interface OtelResourceConfig {
  serviceName: string;
  attributes: {
    "deployment.environment": string;
  };
}

/** registerOTel 호출에 필요한 resource 설정을 env 로부터 산출한다. */
export function resolveOtelResourceConfig(env: OtelResourceEnv): OtelResourceConfig {
  return {
    serviceName: resolveServiceName(env.OTEL_SERVICE_NAME),
    attributes: {
      "deployment.environment": resolveDeploymentEnvironment(env.APP_ENV),
    },
  };
}

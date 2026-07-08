/**
 * Next.js 서버 런타임 OTel 계측 부트스트랩.
 * Next 가 프로세스 부팅 시 1회 `register()`를 호출한다(`next.config.mjs`의
 * `experimental.instrumentationHook: true` 필요).
 *
 * 근거: design-fe-web.md "시스템 역할 경계" · "계측 config 계약".
 */
import { registerOTel } from "@vercel/otel";
import { resolveOtelResourceConfig } from "@/lib/server/otel-resource";

export function register(): void {
  const resourceConfig = resolveOtelResourceConfig({
    OTEL_SERVICE_NAME: process.env["OTEL_SERVICE_NAME"],
    APP_ENV: process.env["APP_ENV"],
  });

  try {
    // OTEL_EXPORTER_OTLP_ENDPOINT 가 미설정이면 @vercel/otel 은 export 없이 no-op 등록한다.
    registerOTel({
      serviceName: resourceConfig.serviceName,
      attributes: resourceConfig.attributes,
    });
  } catch (error) {
    // OTel 등록 실패가 앱 부팅을 막아서는 안 된다(장애 격리) — Collector 미가동·설정
    // 오류가 있어도 요청 처리는 계측 없이 정상 동작해야 한다.
    console.warn(
      "[instrumentation] OTel 등록에 실패했습니다. 계측 없이 앱을 계속 구동합니다.",
      error
    );
  }
}

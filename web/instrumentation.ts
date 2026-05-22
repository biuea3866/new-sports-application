import { registerOTel } from "@vercel/otel";

/**
 * Next.js 서버 사이드 트레이싱 (SSR · Route Handler · Server Component).
 *
 * @vercel/otel 이 fetch 를 자동 계측하므로, BFF Route Handler 가 BACKEND_URL 을
 * 호출할 때 W3C traceparent 헤더가 전파됩니다 → 백엔드 트레이스와 한 트레이스로 연결.
 *
 * 트레이스 전송 대상은 OTEL_EXPORTER_OTLP_ENDPOINT 환경변수로 지정 (OTel Collector).
 */
export function register() {
  registerOTel({ serviceName: "sports-web" });
}

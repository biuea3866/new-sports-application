/**
 * tracing.ts — OpenTelemetry 트레이서 설정 (자체 호스팅, dev 한정)
 *
 * 모바일 API 호출 트레이스를 OTel Collector(OTLP HTTP)로 전송합니다.
 *
 * 주의: 전송 대상은 로컬 자체 호스팅 스택입니다. dev/emulator 에서만 도달 가능하며
 * prod 빌드에서는 Collector 에 닿지 못합니다 — 전송 실패는 무시되어 앱 동작에 영향이 없습니다.
 * Android 에뮬레이터는 EXPO_PUBLIC_OTLP_ENDPOINT 를 http://10.0.2.2:4318 로 설정하세요.
 */
import { W3CTraceContextPropagator } from '@opentelemetry/core';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { resourceFromAttributes } from '@opentelemetry/resources';
import { BasicTracerProvider, BatchSpanProcessor } from '@opentelemetry/sdk-trace-base';
import { ATTR_SERVICE_NAME } from '@opentelemetry/semantic-conventions';

const OTLP_ENDPOINT = process.env.EXPO_PUBLIC_OTLP_ENDPOINT ?? 'http://localhost:4318';

const exporter = new OTLPTraceExporter({
  url: `${OTLP_ENDPOINT}/v1/traces`,
});

const provider = new BasicTracerProvider({
  resource: resourceFromAttributes({
    [ATTR_SERVICE_NAME]: 'sports-mobile',
  }),
  spanProcessors: [new BatchSpanProcessor(exporter)],
});

/** API 호출 계측에 사용하는 트레이서 */
export const tracer = provider.getTracer('sports-mobile');

/** 백엔드로 traceparent 헤더를 주입하는 W3C 전파기 */
export const propagator = new W3CTraceContextPropagator();

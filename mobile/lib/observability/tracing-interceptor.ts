/**
 * tracing-interceptor.ts — axios 요청을 OpenTelemetry span 으로 계측.
 *
 * 각 API 호출마다 CLIENT span 을 생성하고 W3C traceparent 헤더를 주입해
 * 백엔드 트레이스와 한 트레이스로 연결합니다.
 * RN 에는 자동 계측이 없어 axios 인터셉터로 수동 계측합니다.
 */
import { context, SpanKind, SpanStatusCode, trace, type Span } from '@opentelemetry/api';
import type { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios';

import { propagator, tracer } from './tracing';

/** 요청 config ↔ span 연결 (config 를 변형하지 않고 약한 참조로 보관) */
const requestSpans = new WeakMap<object, Span>();

export function attachTracingInterceptors(client: AxiosInstance): void {
  client.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    const method = (config.method ?? 'get').toUpperCase();
    const span = tracer.startSpan(`HTTP ${method} ${config.url ?? ''}`, {
      kind: SpanKind.CLIENT,
      attributes: {
        'http.request.method': method,
        'url.full': `${config.baseURL ?? ''}${config.url ?? ''}`,
      },
    });

    // traceparent 헤더 주입 → 백엔드가 같은 트레이스로 이어받음
    const carrier: Record<string, string> = {};
    propagator.inject(trace.setSpan(context.active(), span), carrier, {
      set: (target, key, value) => {
        target[key] = value;
      },
    });
    Object.entries(carrier).forEach(([key, value]) => {
      config.headers.set(key, value);
    });

    requestSpans.set(config, span);
    return config;
  });

  client.interceptors.response.use(
    (response) => {
      const span = requestSpans.get(response.config);
      if (span) {
        span.setAttribute('http.response.status_code', response.status);
        span.setStatus({ code: SpanStatusCode.OK });
        span.end();
        requestSpans.delete(response.config);
      }
      return response;
    },
    (error: unknown) => {
      const config = (error as AxiosError).config;
      if (config) {
        const span = requestSpans.get(config);
        if (span) {
          const status = (error as AxiosError).response?.status;
          if (status !== undefined) {
            span.setAttribute('http.response.status_code', status);
          }
          span.setStatus({
            code: SpanStatusCode.ERROR,
            message: error instanceof Error ? error.message : 'request failed',
          });
          span.end();
          requestSpans.delete(config);
        }
      }
      return Promise.reject(error);
    }
  );
}

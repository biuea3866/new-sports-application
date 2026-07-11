/**
 * limitedDrops.ts — 한정판 회차 조회·구매 API 함수
 *
 * BE API 계약(TDD "API 계약"): GET /limited-drops/{dropId}, POST /limited-drops/{dropId}/orders
 * 구매 실패(425/409/429/403)는 정상 실패 경로이므로 예외를 던지지 않고
 * LimitedDropPurchaseResult 판별 유니온으로 반환한다. 5xx·네트워크 오류는 그대로 전파한다.
 *
 * FE-08: 가상 대기열 경유 구매는 `entryToken`(대기실에서 발급된 입장 토큰)이 있으면
 * `X-Entry-Token` 헤더를 부착한다(경로·body 불변). 토큰 없이 대기열을 우회한 구매는
 * BE가 403 code=QUEUE_BYPASS_DENIED로 거부하며 BYPASS_DENIED outcome으로 매핑한다.
 */
import { AxiosError } from 'axios';

import { getBeClient } from './be-client';
import type {
  LimitedDropApiErrorBody,
  LimitedDropPurchaseResponse,
  LimitedDropPurchaseResult,
  LimitedDropResponse,
  PurchaseLimitedDropRequest,
} from './types';

export interface PurchaseLimitedDropOptions {
  userId: number;
  idempotencyKey: string;
  /** 대기실에서 발급된 가상 대기열 입장 토큰. 있으면 X-Entry-Token 헤더로 부착한다(FE-08). */
  entryToken?: string;
}

export async function getLimitedDrop(dropId: number): Promise<LimitedDropResponse> {
  const response = await getBeClient().get<LimitedDropResponse>(`/limited-drops/${dropId}`);
  return response.data;
}

export async function purchaseLimitedDrop(
  dropId: number,
  body: PurchaseLimitedDropRequest,
  options: PurchaseLimitedDropOptions
): Promise<LimitedDropPurchaseResult> {
  try {
    const response = await getBeClient().post<LimitedDropPurchaseResponse>(
      `/limited-drops/${dropId}/orders`,
      body,
      {
        headers: {
          'X-User-Id': String(options.userId),
          'Idempotency-Key': options.idempotencyKey,
          ...(options.entryToken ? { 'X-Entry-Token': options.entryToken } : {}),
        },
      }
    );
    return { outcome: 'ADMITTED', data: response.data };
  } catch (error) {
    return mapPurchaseFailure(error);
  }
}

function mapPurchaseFailure(error: unknown): LimitedDropPurchaseResult {
  if (!(error instanceof AxiosError) || !error.response) {
    throw error;
  }

  const errorBody = error.response.data as LimitedDropApiErrorBody | undefined;

  switch (error.response.status) {
    case 425:
      return { outcome: 'TOO_EARLY', openAt: errorBody?.openAt ?? null };
    case 409:
      // BE ProblemDetail의 실제 code 값은 LIMITED_DROP_CLOSED / LIMITED_DROP_SOLD_OUT이다
      // (GlobalExceptionHandler + LimitedDropClosedException/LimitedDropSoldOutException 참고).
      // code가 없거나 다른 값이면 SOLD_OUT으로 기본 처리한다.
      return errorBody?.code === 'LIMITED_DROP_CLOSED'
        ? { outcome: 'CLOSED' }
        : { outcome: 'SOLD_OUT' };
    case 429:
      return { outcome: 'THROTTLED' };
    case 403:
      return errorBody?.code === 'QUEUE_BYPASS_DENIED'
        ? { outcome: 'BYPASS_DENIED' }
        : { outcome: 'LIMIT_EXCEEDED' };
    default:
      throw error;
  }
}

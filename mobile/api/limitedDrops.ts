/**
 * limitedDrops.ts — 한정판 회차 조회·구매 API 함수
 *
 * BE API 계약(TDD "API 계약"): GET /limited-drops/{dropId}, POST /limited-drops/{dropId}/orders
 * 구매 실패(425/409/429/403)는 정상 실패 경로이므로 예외를 던지지 않고
 * LimitedDropPurchaseResult 판별 유니온으로 반환한다. 5xx·네트워크 오류는 그대로 전파한다.
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
      return errorBody?.code === 'CLOSED' ? { outcome: 'CLOSED' } : { outcome: 'SOLD_OUT' };
    case 429:
      return { outcome: 'THROTTLED' };
    case 403:
      return { outcome: 'LIMIT_EXCEEDED' };
    default:
      throw error;
  }
}

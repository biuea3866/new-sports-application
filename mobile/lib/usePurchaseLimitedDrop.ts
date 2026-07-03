/**
 * usePurchaseLimitedDrop — POST /limited-drops/{dropId}/orders 구매 mutation 훅
 *
 * 근거: design-fe-app.md "API 연동 표" · "실패 경로·엣지"
 * purchaseLimitedDrop이 반환하는 LimitedDropPurchaseResult(정상 실패 경로)와
 * 5xx·네트워크 오류(예외 전파)를 화면이 분기하기 쉬운 단일 판별 유니온(PurchaseLimitedDropPhase)으로 통합한다.
 * 429(THROTTLED)는 BE 멱등 정합을 위해 동일 Idempotency-Key로 1회 자동 재시도한다.
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useCurrentUserId } from '../api/goods';
import { purchaseLimitedDrop } from '../api/limitedDrops';
import type { LimitedDropPurchaseResponse, LimitedDropPurchaseResult } from '../api/types';

export interface PurchaseLimitedDropVariables {
  quantity: number;
}

export type PurchaseLimitedDropPhase =
  | { phase: 'admitted'; data: LimitedDropPurchaseResponse }
  | { phase: 'tooEarly'; openAt: string | null }
  | { phase: 'soldOut' }
  | { phase: 'closed' }
  | { phase: 'throttled' }
  | { phase: 'limit' }
  | { phase: 'error' };

const MAX_THROTTLE_RETRIES = 1;

function generateIdempotencyKey(): string {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (char) => {
    const random = (Math.random() * 16) | 0;
    const value = char === 'x' ? random : (random & 0x3) | 0x8;
    return value.toString(16);
  });
}

function toPhase(result: LimitedDropPurchaseResult): PurchaseLimitedDropPhase {
  switch (result.outcome) {
    case 'ADMITTED':
      return { phase: 'admitted', data: result.data };
    case 'TOO_EARLY':
      return { phase: 'tooEarly', openAt: result.openAt };
    case 'SOLD_OUT':
      return { phase: 'soldOut' };
    case 'CLOSED':
      return { phase: 'closed' };
    case 'THROTTLED':
      return { phase: 'throttled' };
    case 'LIMIT_EXCEEDED':
      return { phase: 'limit' };
  }
}

/**
 * 동일 idempotencyKey로 구매를 시도하고, THROTTLED 응답이면 최대 MAX_THROTTLE_RETRIES회
 * 동일 키로 재시도한다. 5xx·네트워크 오류는 error phase로 흡수해 mutation을 항상 성공시킨다.
 */
async function attemptPurchase(
  dropId: number,
  quantity: number,
  userId: number,
  idempotencyKey: string
): Promise<PurchaseLimitedDropPhase> {
  try {
    let result = await purchaseLimitedDrop(dropId, { quantity }, { userId, idempotencyKey });
    let retryCount = 0;
    while (result.outcome === 'THROTTLED' && retryCount < MAX_THROTTLE_RETRIES) {
      result = await purchaseLimitedDrop(dropId, { quantity }, { userId, idempotencyKey });
      retryCount += 1;
    }
    return toPhase(result);
  } catch {
    return { phase: 'error' };
  }
}

export function usePurchaseLimitedDrop(dropId: number) {
  const queryClient = useQueryClient();
  const userId = useCurrentUserId();

  return useMutation<PurchaseLimitedDropPhase, never, PurchaseLimitedDropVariables>({
    mutationFn: ({ quantity }) =>
      attemptPurchase(dropId, quantity, userId, generateIdempotencyKey()),
    onSuccess: (result) => {
      if (result.phase === 'admitted') {
        void queryClient.invalidateQueries({ queryKey: ['limitedDrops', dropId] });
      }
    },
  });
}

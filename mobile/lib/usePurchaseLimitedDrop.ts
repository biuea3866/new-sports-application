/**
 * usePurchaseLimitedDrop — POST /limited-drops/{dropId}/orders 구매 mutation 훅
 *
 * 근거: design-fe-app.md "API 연동 표" · "실패 경로·엣지"
 * purchaseLimitedDrop이 반환하는 LimitedDropPurchaseResult(정상 실패 경로)와
 * 5xx·네트워크 오류(예외 전파)를 화면이 분기하기 쉬운 단일 판별 유니온(PurchaseLimitedDropPhase)으로 통합한다.
 * 429(THROTTLED)는 BE 멱등 정합을 위해 동일 Idempotency-Key로 1회 자동 재시도한다.
 * error·throttled phase 이후 사용자가 재시도(재-mutate)하면 동일 구매 시도(intent)로 간주해
 * 같은 Idempotency-Key를 재사용한다 — 원 요청이 서버엔 도달했으나 응답만 실패한 경우
 * 새 키로 재시도하면 중복 주문이 생기기 때문이다. admitted 등 확정 결과 이후의 다음 mutate는
 * 새 구매 시도로 보고 새 키를 발급한다.
 *
 * FE-08: `entryTokenStore`에 저장된 입장 토큰(대기실에서 발급, target `limited-drop:{dropId}`)이
 * 있으면 구매 요청에 실어 X-Entry-Token 헤더로 부착한다. 토큰이 없거나 만료됐으면(tokenFor가
 * null 반환) 헤더 없이 기존과 동일하게 호출한다. BE가 403 code=QUEUE_BYPASS_DENIED로 거부하면
 * bypassDenied phase로 노출해 화면이 "다시 대기하기"(대기실 재진입)를 안내한다.
 */
import { useRef } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';

import { useCurrentUserId } from '../api/goods';
import { purchaseLimitedDrop } from '../api/limitedDrops';
import type { LimitedDropPurchaseResponse, LimitedDropPurchaseResult } from '../api/types';
import { useEntryTokenStore } from './entryTokenStore';

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
  | { phase: 'bypassDenied' }
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
    case 'BYPASS_DENIED':
      return { phase: 'bypassDenied' };
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
  idempotencyKey: string,
  entryToken: string | null
): Promise<PurchaseLimitedDropPhase> {
  const options = { userId, idempotencyKey, ...(entryToken ? { entryToken } : {}) };
  try {
    let result = await purchaseLimitedDrop(dropId, { quantity }, options);
    let retryCount = 0;
    while (result.outcome === 'THROTTLED' && retryCount < MAX_THROTTLE_RETRIES) {
      result = await purchaseLimitedDrop(dropId, { quantity }, options);
      retryCount += 1;
    }
    return toPhase(result);
  } catch {
    return { phase: 'error' };
  }
}

const RETRYABLE_SAME_KEY_PHASES: ReadonlySet<PurchaseLimitedDropPhase['phase']> = new Set([
  'error',
  'throttled',
]);

export function usePurchaseLimitedDrop(dropId: number) {
  const queryClient = useQueryClient();
  const userId = useCurrentUserId();
  const currentIntentKeyRef = useRef<string | null>(null);
  const tokenFor = useEntryTokenStore((state) => state.tokenFor);

  return useMutation<PurchaseLimitedDropPhase, never, PurchaseLimitedDropVariables>({
    mutationFn: ({ quantity }) => {
      const idempotencyKey = currentIntentKeyRef.current ?? generateIdempotencyKey();
      currentIntentKeyRef.current = idempotencyKey;
      const entryToken = tokenFor('limited-drop', dropId);
      return attemptPurchase(dropId, quantity, userId, idempotencyKey, entryToken);
    },
    onSuccess: (result) => {
      currentIntentKeyRef.current = RETRYABLE_SAME_KEY_PHASES.has(result.phase)
        ? currentIntentKeyRef.current
        : null;
      if (result.phase === 'admitted') {
        void queryClient.invalidateQueries({ queryKey: ['limitedDrops', dropId] });
      }
    },
  });
}

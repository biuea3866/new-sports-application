/**
 * useEnterQueue — 대기열 진입 mutation 훅
 *
 * 근거: 티켓 FE-05, `20260709-가상대기열-design-fe-app.md` "API 연동 표"(S1 진입) · "컴포넌트 트리".
 * `enterQueue` api(FE-01)를 TanStack Query `useMutation`으로 감싸 화면(뷰모델)이 분기하기 쉬운
 * 판별 유니온 결과를 그대로 노출한다.
 *
 * - 성공(ENTERED) 응답에 입장 토큰(ADMITTED/DIRECT_ADMITTED)이 있으면 `entryTokenStore`에 저장한다.
 * - 429 포화는 `enterQueue`가 이미 throw 없이 `FULL` 판별로 반환하므로 그대로 노출한다(에러 아님).
 * - 5xx·네트워크 오류는 `enterQueue`가 그대로 throw하므로 mutation error 상태로 전파된다
 *   (자동 재시도 없음 — PRD 악순환 방지, 재시도는 화면의 수동 CTA가 재-mutate).
 */
import { useMutation } from '@tanstack/react-query';

import { useCurrentUserId } from '../api/goods';
import { enterQueue, type QueueEnterResult, type QueueTargetType } from '../api/virtualQueue';
import { useEntryTokenStore } from './entryTokenStore';

export function useEnterQueue(type: QueueTargetType, targetId: number) {
  const userId = useCurrentUserId();
  const setToken = useEntryTokenStore((state) => state.setToken);

  return useMutation<QueueEnterResult, Error, void>({
    mutationFn: () => enterQueue(type, targetId, userId),
    onSuccess: (result) => {
      if (result.outcome !== 'ENTERED') {
        return;
      }
      const { entryToken, tokenExpiresAt } = result.data;
      if (entryToken && tokenExpiresAt) {
        setToken(type, targetId, entryToken, tokenExpiresAt);
      }
    },
  });
}

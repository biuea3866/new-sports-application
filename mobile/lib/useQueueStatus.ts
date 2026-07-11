/**
 * useQueueStatus — GET /virtual-queues/{type}/{targetId}/entries/me 순번·상태 폴링 훅
 *
 * 근거: `20260709-가상대기열-design-fe-app.md` "API 연동 표" · "상태관리 설계".
 * 3초 폴링이 heartbeat를 겸한다(BE 60초 미갱신 시 이탈 판정) — 별도 heartbeat 호출은 없다.
 *
 * - 큐 상태는 서버 상태 — TanStack Query 캐시가 SSOT다. 스토어에 복사하지 않는다.
 * - `enabled`로 폴링 시작/중단을 제어한다(진입 성공 후 활성화, ADMITTED/DIRECT_ADMITTED/포화/404
 *   도달 시 상위 뷰모델이 `enabled=false`로 전환).
 * - 결과는 FE-01 `QueueStatusResult`(`OK`|`NOT_IN_QUEUE`) 그대로 노출한다. 404는 예외가 아니라
 *   `NOT_IN_QUEUE` 데이터로 반환되므로(이탈 감지는 상위 뷰모델이 `outcome`으로 판단), 5xx·네트워크
 *   오류만 query error로 남는다.
 * - 연속 3회 실패 시 자동 재조회를 중단한다(`lib/useLimitedDrop.ts#getRefetchIntervalMs` 폴링 관례
 *   재사용, `POLL_INTERVAL_MS` 3초). 이후 재개는 상위 뷰모델의 수동 재시도(refetch)로만 이뤄진다.
 *   TanStack의 `query.state.fetchFailureCount`는 새 폴링이 시작될 때마다 0으로 리셋되는 "단일
 *   fetch 내부 재시도 횟수"라 폴링 사이클을 넘어 누적되지 않는다 — 그래서 "연속 폴링 실패 횟수"는
 *   이 훅이 직접(ref) 세어 `refetchInterval`에 전달한다.
 * - **`consecutiveFailureCount`를 반환값에 노출한다.** TanStack Query는 최초 성공 이후의
 *   백그라운드 실패에서도 직전 성공 `data`를 보존한다(`status`는 `'error'`로 전환되지만
 *   `data`는 stale 값 그대로) — 그래서 상위 뷰모델이 `data` 유무만으로 성공/실패를 가르면
 *   지속 실패를 "그대로 성공"으로 오판한다. 뷰모델은 이 카운트가 `MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES`에
 *   도달했는지로 error 전이를 판단해야 한다(design-fe-app.md "5xx→error(3회 후 중단)").
 */
import { useRef } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { UseQueryResult } from '@tanstack/react-query';

import { getQueueStatus } from '../api/virtualQueue';
import type { QueueStatusResult, QueueTargetType } from '../api/virtualQueue';

const POLL_INTERVAL_MS = 3 * 1000;

/**
 * 연속 폴링 실패 허용 횟수 — 이 값에 도달하면 ① `refetchInterval`이 자동 재조회를 멈추고
 * ② 상위 뷰모델(`useWaitingRoom`)이 error phase로 전이한다. 두 판단이 반드시 같은 값을 써야
 * "폴링은 멈췄는데 화면은 waiting에 프리즈" 같은 불일치가 생기지 않는다.
 */
export const MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES = 3;

/**
 * 직전까지의 연속 실패 횟수로부터 다음 폴링 간격을 결정한다.
 * 연속 3회 실패하면 더 이상 스스로 재조회하지 않는다(false) — 수동 재시도만 남는다.
 */
export function getQueueStatusRefetchIntervalMs(consecutiveFailureCount: number): number | false {
  return consecutiveFailureCount >= MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES
    ? false
    : POLL_INTERVAL_MS;
}

export type QueueStatusQueryResult = UseQueryResult<QueueStatusResult, Error> & {
  /** 마지막 성공 이후 연속 실패 횟수. 성공 시 0으로 리셋된다. */
  consecutiveFailureCount: number;
};

export function useQueueStatus(
  type: QueueTargetType,
  targetId: number,
  userId: number,
  enabled: boolean
): QueueStatusQueryResult {
  const consecutiveFailureCountRef = useRef(0);

  const query = useQuery<QueueStatusResult, Error>({
    queryKey: ['virtualQueue', type, targetId],
    queryFn: async () => {
      try {
        const result = await getQueueStatus(type, targetId, userId);
        consecutiveFailureCountRef.current = 0;
        return result;
      } catch (error) {
        consecutiveFailureCountRef.current += 1;
        throw error;
      }
    },
    enabled,
    // 폴링 시도 자체가 "연속 실패 횟수"의 단위여야 하므로, TanStack의 단일 fetch 내부 재시도는
    // 끈다 — 재시도가 켜져 있으면 폴링 한 번 안에서도 실패 횟수가 늘어나 판단이 흐트러진다.
    retry: false,
    refetchInterval: () => getQueueStatusRefetchIntervalMs(consecutiveFailureCountRef.current),
  });

  return { ...query, consecutiveFailureCount: consecutiveFailureCountRef.current };
}

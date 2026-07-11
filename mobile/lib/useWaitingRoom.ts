/**
 * useWaitingRoom — 대기실 화면(S1)의 뷰모델 훅
 *
 * 근거: 티켓 FE-07, `20260709-가상대기열-design-fe-app.md` "S1 텍스트 와이어프레임" ·
 * "화면별 상태 표" · "컴포넌트 트리" · "상태관리 설계".
 *
 * useEnterQueue(FE-05)로 진입 → 성공 시 useQueueStatus(FE-06)로 3초 폴링을 시작한다.
 * 두 훅의 응답을 내부 상태 머신(internalPhase)으로 합성해 화면이 그대로 렌더할 수 있는
 * 단일 phase 판별 유니온(loading/waiting/admitted/empty/full/error)을 반환한다.
 * 로직은 전부 이 훅에 있다 — 화면 컴포넌트는 phase별 렌더만 담당한다(no-logic-in-component).
 *
 * - ADMITTED/DIRECT_ADMITTED(entryToken 존재) 도달 시 entryTokenStore(FE-02)에 토큰을 저장하고
 *   `router.replace`로 구매 화면(한정판 purchase / 티케팅 event order)으로 전환한다. 폴링은 중단된다.
 * - 최초 WAITING 응답의 aheadCount를 기준값(ref)으로 저장해 진행바 ratio를 계산한다
 *   (waitingRoomPresentation#computeProgressRatio — 기준값 없거나 0이면 ratio는 null).
 * - 404(NOT_IN_QUEUE) → empty(재-enter CTA), 429(FULL) → full(재-enter CTA),
 *   5xx/네트워크 오류 → error(진입 단계 오류는 재-enter, 폴링 단계 오류는 상태 재조회로 재시도).
 * - internalPhase는 useState로 관리해, retry로 재진입해도(과거 NOT_IN_QUEUE/FULL 캐시에 갇히지 않고)
 *   새 enter 응답이 오면 즉시 다음 상태로 전이한다.
 * - **폴링 실패 전이는 `statusQuery.consecutiveFailureCount`로 판단한다.** TanStack Query는
 *   최초 성공 이후의 백그라운드 실패에서도 직전 성공 `data`를 보존하므로(`useQueueStatus` 주석
 *   참조), `data` 존재 여부만으로 성공/실패를 가르면 지속 실패를 놓친다(stale 순번에 프리즈).
 *   `MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES`(3) 도달 시에만 error phase로 전이해, 폴링이
 *   스스로 멈추는 시점(`useQueueStatus#getQueueStatusRefetchIntervalMs`)과 정합을 맞춘다
 *   (design-fe-app.md "5xx→error(3회 후 중단)").
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'expo-router';

import { useCurrentUserId } from '../api/goods';
import { leaveQueue } from '../api/virtualQueue';
import type { QueueEntryResponse, QueueTargetType } from '../api/virtualQueue';
import { useEntryTokenStore } from './entryTokenStore';
import { useEnterQueue } from './useEnterQueue';
import { MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES, useQueueStatus } from './useQueueStatus';
import { ROUTES } from './navigation';
import {
  computeProgressRatio,
  formatEtaLabel,
  formatPercentLabel,
} from './waitingRoomPresentation';

export type WaitingRoomPhase =
  | { phase: 'loading'; leave: () => void }
  | {
      phase: 'waiting';
      position: number | null;
      aheadCount: number | null;
      etaLabel: string | null;
      ratio: number | null;
      percentLabel: string | null;
      leave: () => void;
    }
  | { phase: 'admitted'; leave: () => void }
  | { phase: 'empty'; retry: () => void; leave: () => void }
  | { phase: 'full'; retry: () => void; leave: () => void }
  | { phase: 'error'; retry: () => void; leave: () => void };

/**
 * 'waiting' phase 렌더에 실제로 쓰이는 필드만 담은 스냅샷.
 * 서버 응답(QueueEntryResponse) 전체를 복사 보관하지 않는다(no-global-by-default 확장 —
 * 지역 useState도 서버 데이터를 필요 이상으로 복제하지 않는다). status/entryToken/tokenExpiresAt은
 * 전이 시점(isAdmittedEntry/handleAdmission)에만 쓰이고 phase 판별자로 남길 필요가 없다.
 */
interface WaitingEntrySnapshot {
  position: number | null;
  aheadCount: number | null;
  etaSeconds: number | null;
}

function toWaitingEntrySnapshot(entry: QueueEntryResponse): WaitingEntrySnapshot {
  return { position: entry.position, aheadCount: entry.aheadCount, etaSeconds: entry.etaSeconds };
}

type InternalPhase =
  | { kind: 'loading' }
  | { kind: 'waiting'; entry: WaitingEntrySnapshot }
  | { kind: 'admitted' }
  | { kind: 'empty' }
  | { kind: 'full' }
  | { kind: 'error'; source: 'enter' | 'status' };

function isAdmittedEntry(entry: QueueEntryResponse): boolean {
  return entry.status === 'ADMITTED' || entry.status === 'DIRECT_ADMITTED';
}

function destinationFor(type: QueueTargetType, targetId: number): string {
  return type === 'limited-drop'
    ? ROUTES.limitedDrop.purchase(String(targetId))
    : ROUTES.event.order(String(targetId));
}

export function useWaitingRoom(type: QueueTargetType, targetId: number): WaitingRoomPhase {
  const router = useRouter();
  const userId = useCurrentUserId();
  const setToken = useEntryTokenStore((state) => state.setToken);

  const [internalPhase, setInternalPhase] = useState<InternalPhase>({ kind: 'loading' });
  const initialAheadCountRef = useRef<number | null>(null);
  const admissionHandledRef = useRef(false);

  const enterMutation = useEnterQueue(type, targetId);
  const statusQuery = useQueueStatus(type, targetId, userId, internalPhase.kind === 'waiting');

  const trackInitialAheadCount = useCallback((aheadCount: number | null) => {
    if (initialAheadCountRef.current === null && aheadCount !== null && aheadCount > 0) {
      initialAheadCountRef.current = aheadCount;
    }
  }, []);

  const handleAdmission = useCallback(
    (entry: QueueEntryResponse) => {
      setInternalPhase({ kind: 'admitted' });
      if (admissionHandledRef.current) {
        return;
      }
      admissionHandledRef.current = true;
      if (entry.entryToken && entry.tokenExpiresAt) {
        setToken(type, targetId, entry.entryToken, entry.tokenExpiresAt);
      }
      router.replace(destinationFor(type, targetId));
    },
    [router, setToken, type, targetId]
  );

  // 최초 1회 진입(enter) — 마운트 시 한 번만 호출한다.
  const mutateEnterRef = useRef(enterMutation.mutate);
  mutateEnterRef.current = enterMutation.mutate;
  const hasStartedRef = useRef(false);
  useEffect(() => {
    if (hasStartedRef.current) {
      return;
    }
    hasStartedRef.current = true;
    mutateEnterRef.current();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // enter mutation 응답 → 내부 상태 전이
  useEffect(() => {
    if (enterMutation.isError) {
      setInternalPhase({ kind: 'error', source: 'enter' });
      return;
    }
    if (!enterMutation.data) {
      return;
    }
    if (enterMutation.data.outcome === 'FULL') {
      setInternalPhase({ kind: 'full' });
      return;
    }
    const entry = enterMutation.data.data;
    if (isAdmittedEntry(entry)) {
      handleAdmission(entry);
      return;
    }
    trackInitialAheadCount(entry.aheadCount);
    setInternalPhase({ kind: 'waiting', entry: toWaitingEntrySnapshot(entry) });
  }, [enterMutation.data, enterMutation.isError, handleAdmission, trackInitialAheadCount]);

  // 폴링(heartbeat) 응답 → 내부 상태 전이
  //
  // isError를 data 존재 여부보다 먼저 판단한다 — TanStack Query는 최초 성공 이후의 실패에서도
  // 직전 성공 data를 보존하므로(`useQueueStatus` 주석), data 우선으로 분기하면 지속 실패를
  // stale data로 계속 "성공"처럼 처리해 화면이 순번에 프리즈된다. 연속 실패가
  // MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES(3)에 도달하기 전에는(일시 오류 관용) 아무 것도 하지
  // 않고 현재 phase(직전에 확인된 순번)를 유지한다 — 3회 도달 시에만 error로 전이한다.
  useEffect(() => {
    if (statusQuery.isError) {
      if (statusQuery.consecutiveFailureCount >= MAX_CONSECUTIVE_QUEUE_STATUS_FAILURES) {
        setInternalPhase((current) =>
          current.kind === 'waiting' ? { kind: 'error', source: 'status' } : current
        );
      }
      return;
    }
    if (!statusQuery.data) {
      return;
    }
    if (statusQuery.data.outcome === 'NOT_IN_QUEUE') {
      setInternalPhase({ kind: 'empty' });
      return;
    }
    const entry = statusQuery.data.data;
    if (isAdmittedEntry(entry)) {
      handleAdmission(entry);
      return;
    }
    trackInitialAheadCount(entry.aheadCount);
    setInternalPhase({ kind: 'waiting', entry: toWaitingEntrySnapshot(entry) });
  }, [
    statusQuery.data,
    statusQuery.isError,
    statusQuery.consecutiveFailureCount,
    handleAdmission,
    trackInitialAheadCount,
  ]);

  const retryEnter = useCallback(() => {
    enterMutation.mutate();
  }, [enterMutation]);

  const retryStatus = useCallback(() => {
    statusQuery.refetch();
  }, [statusQuery]);

  const leave = useCallback(() => {
    leaveQueue(type, targetId, userId).catch(() => undefined);
  }, [type, targetId, userId]);

  switch (internalPhase.kind) {
    case 'loading':
      return { phase: 'loading', leave };
    case 'waiting': {
      const { entry } = internalPhase;
      const ratio = computeProgressRatio(entry.aheadCount, initialAheadCountRef.current);
      return {
        phase: 'waiting',
        position: entry.position,
        aheadCount: entry.aheadCount,
        etaLabel: formatEtaLabel(entry.etaSeconds),
        ratio,
        percentLabel: formatPercentLabel(ratio),
        leave,
      };
    }
    case 'admitted':
      return { phase: 'admitted', leave };
    case 'empty':
      return { phase: 'empty', retry: retryEnter, leave };
    case 'full':
      return { phase: 'full', retry: retryEnter, leave };
    case 'error':
      return {
        phase: 'error',
        retry: internalPhase.source === 'status' ? retryStatus : retryEnter,
        leave,
      };
  }
}

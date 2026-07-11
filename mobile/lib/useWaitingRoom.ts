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
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { useRouter } from 'expo-router';

import { useCurrentUserId } from '../api/goods';
import { leaveQueue } from '../api/virtualQueue';
import type { QueueEntryResponse, QueueTargetType } from '../api/virtualQueue';
import { useEntryTokenStore } from './entryTokenStore';
import { useEnterQueue } from './useEnterQueue';
import { useQueueStatus } from './useQueueStatus';
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

type InternalPhase =
  | { kind: 'loading' }
  | { kind: 'waiting'; entry: QueueEntryResponse }
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
    setInternalPhase({ kind: 'waiting', entry });
  }, [enterMutation.data, enterMutation.isError, handleAdmission, trackInitialAheadCount]);

  // 폴링(heartbeat) 응답 → 내부 상태 전이
  useEffect(() => {
    if (statusQuery.data) {
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
      setInternalPhase({ kind: 'waiting', entry });
      return;
    }
    if (statusQuery.isError) {
      setInternalPhase((current) =>
        current.kind === 'waiting' ? { kind: 'error', source: 'status' } : current
      );
    }
  }, [statusQuery.data, statusQuery.isError, handleAdmission, trackInitialAheadCount]);

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

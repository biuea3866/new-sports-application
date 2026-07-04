/**
 * useCountdown — openAt까지 남은 시간을 초당 갱신하는 지역 UI 상태 훅
 *
 * 근거: design-fe-app.md "상태관리 설계" (카운트다운 남은 시간 = 지역, useState).
 * 순수 UI 상태이므로 전역 승격하지 않는다. 언마운트 시 interval을 정리해 누수를 방지한다.
 */
import { useEffect, useState } from 'react';

const TICK_INTERVAL_MS = 1000;

export interface UseCountdownResult {
  remainingMs: number;
  isOpen: boolean;
}

function computeRemainingMs(openAtIso: string): number {
  const remainingMs = new Date(openAtIso).getTime() - Date.now();
  return remainingMs > 0 ? remainingMs : 0;
}

export function useCountdown(openAtIso: string): UseCountdownResult {
  const [remainingMs, setRemainingMs] = useState(() => computeRemainingMs(openAtIso));

  useEffect(() => {
    setRemainingMs(computeRemainingMs(openAtIso));

    const intervalId = setInterval(() => {
      setRemainingMs(computeRemainingMs(openAtIso));
    }, TICK_INTERVAL_MS);

    return () => clearInterval(intervalId);
  }, [openAtIso]);

  return { remainingMs, isOpen: remainingMs <= 0 };
}

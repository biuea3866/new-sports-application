/**
 * useLimitedDropDetail — S1 상세·카운트다운 화면의 뷰모델 훅
 *
 * 근거: design-fe-app.md "상태관리 설계" · "실패 경로·엣지"
 * useLimitedDrop(서버 상태, Query 캐시가 SSOT)과 useCountdown(카운트다운 지역 상태)을
 * 합성해 화면이 그대로 렌더할 판별 유니온 뷰모델을 만든다. 데이터 가공·분기 로직을
 * 이 훅과 limitedDropDetailPresentation에 모아 화면 컴포넌트는 렌더링만 담당하게 한다.
 */
import { useEffect, useRef } from 'react';

import type { LimitedDropResponse } from '../api/types';
import {
  getCtaConfig,
  getDetailErrorMessage,
  resolveEffectiveStatus,
  type DetailCtaConfig,
} from './limitedDropDetailPresentation';
import { useCountdown } from './useCountdown';
import { useLimitedDrop } from './useLimitedDrop';

// openAt이 없는 회차 조회 실패 상태에서도 useCountdown에 유효한 ISO 문자열을 넘기기 위한 기본값.
const FAR_FUTURE_ISO = '9999-12-31T23:59:59Z';

export type LimitedDropDetailViewModel =
  | { phase: 'loading' }
  | { phase: 'error'; message: string; retry: () => void }
  | {
      phase: 'success';
      drop: LimitedDropResponse;
      remainingMs: number;
      cta: DetailCtaConfig;
    };

export function useLimitedDropDetail(dropId: number): LimitedDropDetailViewModel {
  const { data: drop, isLoading, isError, error, refetch } = useLimitedDrop(dropId);
  const { remainingMs, isOpen } = useCountdown(drop?.openAt ?? FAR_FUTURE_ISO);
  const wasOpenRef = useRef(isOpen);

  useEffect(() => {
    if (!wasOpenRef.current && isOpen && drop?.status === 'SCHEDULED') {
      refetch();
    }
    wasOpenRef.current = isOpen;
  }, [isOpen, drop?.status, refetch]);

  if (isLoading) {
    return { phase: 'loading' };
  }

  if (isError || !drop) {
    return { phase: 'error', message: getDetailErrorMessage(error), retry: refetch };
  }

  const effectiveStatus = resolveEffectiveStatus(drop.status, isOpen);

  return {
    phase: 'success',
    drop,
    remainingMs,
    cta: getCtaConfig(effectiveStatus),
  };
}

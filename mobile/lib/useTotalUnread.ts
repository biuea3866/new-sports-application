/**
 * lib/useTotalUnread.ts — 전역 안읽은 수 합계 훅
 *
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합", design-fe-app.md S1.
 * `useUnreadCounts`(FE-05, 방별 조회)의 Query 캐시를 합산만 한다 — 서버 상태를
 * 별도 스토어에 복사 보관하지 않는다(no-global-by-default).
 */
import { useMemo } from 'react';
import { useUnreadCounts } from './useChat';

/** 모든 방의 안읽은 수 합계를 반환합니다. 데이터 로드 전에는 0을 반환합니다. */
export function useTotalUnread(): number {
  const { data } = useUnreadCounts();

  return useMemo(() => {
    if (!data) {
      return 0;
    }
    return data.reduce((total, item) => total + item.unreadCount, 0);
  }, [data]);
}

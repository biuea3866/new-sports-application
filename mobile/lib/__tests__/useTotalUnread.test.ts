/**
 * lib/useTotalUnread.ts — 전역 안읽은 수 합계 훅 검증
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합" 테스트 케이스
 * "전역 안읽은 수 합계가 탭 배지에 표시되고 0이면 배지가 없다".
 *
 * `useUnreadCounts`(FE-05)가 반환하는 방별 안읽은 수를 합산만 한다 — 서버 상태를
 * 별도 스토어에 복사 보관하지 않는다.
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import { useTotalUnread } from '../useTotalUnread';
import type { RoomUnreadResponse } from '../../api/chat-types';

jest.mock('../../api/chat', () => ({
  getUnreadCounts: jest.fn(),
}));

import { getUnreadCounts } from '../../api/chat';

const getUnreadCountsMock = getUnreadCounts as jest.MockedFunction<typeof getUnreadCounts>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return wrapper;
}

describe('useTotalUnread', () => {
  afterEach(() => jest.clearAllMocks());

  it('방별 안읽은 수를 모두 합산한 값을 반환한다', async () => {
    const response: RoomUnreadResponse[] = [
      { roomId: 1, unreadCount: 3 },
      { roomId: 2, unreadCount: 5 },
    ];
    getUnreadCountsMock.mockResolvedValue(response);
    const wrapper = createWrapper();

    const { result } = renderHook(() => useTotalUnread(), { wrapper });

    await waitFor(() => expect(result.current).toBe(8));
  });

  it('모든 방의 안읽은 수가 0이면 합계도 0을 반환한다', async () => {
    const response: RoomUnreadResponse[] = [
      { roomId: 1, unreadCount: 0 },
      { roomId: 2, unreadCount: 0 },
    ];
    getUnreadCountsMock.mockResolvedValue(response);
    const wrapper = createWrapper();

    const { result } = renderHook(() => useTotalUnread(), { wrapper });

    await waitFor(() => expect(result.current).toBe(0));
  });

  it('조회 전(데이터 없음)에는 0을 반환한다', () => {
    getUnreadCountsMock.mockReturnValue(new Promise(() => undefined));
    const wrapper = createWrapper();

    const { result } = renderHook(() => useTotalUnread(), { wrapper });

    expect(result.current).toBe(0);
  });
});

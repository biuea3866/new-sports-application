/**
 * U-01: useUnreadCounts가 RoomUnreadResponse[]를 방별 매핑으로 반환한다
 * U-02: useMarkRead 성공 후 해당 방의 unread 캐시가 0으로 갱신된다
 * U-03: markRead 실패 시 예외를 전파하되 화면을 막지 않는다(비차단)
 * U-04: useStartGoodsChat이 RoomResponse를 반환하고 기존 방이 있으면 같은 roomId를 받는다
 * U-05: useBackfill이 afterMessageId 이후 메시지만 반환한다(경계값)
 */
import { createElement } from 'react';
import { act } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import {
  UNREAD_COUNTS_QUERY_KEY,
  useBackfill,
  useMarkRead,
  useStartGoodsChat,
  useUnreadCounts,
} from '../useChat';
import type { RoomUnreadResponse } from '../../api/chat-types';
import type { MessageResponse, RoomResponse } from '../../api/types';

jest.mock('../../api/chat', () => ({
  backfillMessages: jest.fn(),
  evictGuest: jest.fn(),
  getUnreadCounts: jest.fn(),
  markRead: jest.fn(),
  startGoodsChat: jest.fn(),
}));

import { backfillMessages, getUnreadCounts, markRead, startGoodsChat } from '../../api/chat';

const getUnreadCountsMock = getUnreadCounts as jest.MockedFunction<typeof getUnreadCounts>;
const markReadMock = markRead as jest.MockedFunction<typeof markRead>;
const startGoodsChatMock = startGoodsChat as jest.MockedFunction<typeof startGoodsChat>;
const backfillMessagesMock = backfillMessages as jest.MockedFunction<typeof backfillMessages>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

describe('useUnreadCounts', () => {
  afterEach(() => jest.clearAllMocks());

  it('[U-01] RoomUnreadResponse[]를 방별 매핑으로 반환한다', async () => {
    const response: RoomUnreadResponse[] = [
      { roomId: 1, unreadCount: 3 },
      { roomId: 2, unreadCount: 0 },
    ];
    getUnreadCountsMock.mockResolvedValue(response);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useUnreadCounts(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual(response);
    expect(result.current.data?.find((item) => item.roomId === 1)?.unreadCount).toBe(3);

    unmount();
    queryClient.clear();
  });
});

describe('useMarkRead', () => {
  afterEach(() => jest.clearAllMocks());

  it('[U-02] 성공 후 해당 방의 unread 캐시가 0으로 갱신된다', async () => {
    const initialCounts: RoomUnreadResponse[] = [
      { roomId: 1, unreadCount: 5 },
      { roomId: 2, unreadCount: 2 },
    ];
    markReadMock.mockResolvedValue({ roomId: 1, unreadCount: 0 });
    const { wrapper, queryClient } = createWrapper();
    queryClient.setQueryData(UNREAD_COUNTS_QUERY_KEY, initialCounts);

    const { result, unmount } = renderHook(() => useMarkRead(), { wrapper });

    await act(async () => {
      await result.current.mutateAsync({ roomId: 1, lastReadMessageId: 42 });
    });

    const cached = queryClient.getQueryData<RoomUnreadResponse[]>(UNREAD_COUNTS_QUERY_KEY);
    expect(cached?.find((item) => item.roomId === 1)?.unreadCount).toBe(0);
    // 다른 방의 unread 수는 영향받지 않는다
    expect(cached?.find((item) => item.roomId === 2)?.unreadCount).toBe(2);

    unmount();
    queryClient.clear();
  });

  it('[U-03] 실패 시 예외를 전파하되 다른 방 캐시는 그대로 유지된다(비차단)', async () => {
    const initialCounts: RoomUnreadResponse[] = [{ roomId: 1, unreadCount: 5 }];
    markReadMock.mockRejectedValue(new Error('Network Error'));
    const { wrapper, queryClient } = createWrapper();
    queryClient.setQueryData(UNREAD_COUNTS_QUERY_KEY, initialCounts);

    const { result, unmount } = renderHook(() => useMarkRead(), { wrapper });

    await act(async () => {
      await expect(
        result.current.mutateAsync({ roomId: 1, lastReadMessageId: 42 })
      ).rejects.toThrow('Network Error');
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    const cached = queryClient.getQueryData<RoomUnreadResponse[]>(UNREAD_COUNTS_QUERY_KEY);
    expect(cached).toEqual(initialCounts);

    unmount();
    queryClient.clear();
  });
});

describe('useStartGoodsChat', () => {
  afterEach(() => jest.clearAllMocks());

  it('[U-04] RoomResponse를 반환하고 기존 방이 있으면 같은 roomId를 받는다', async () => {
    const existingRoom: RoomResponse = { id: 7, type: 'DIRECT', name: null };
    startGoodsChatMock.mockResolvedValue(existingRoom);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useStartGoodsChat(), { wrapper });

    let firstRoom: RoomResponse | undefined;
    let secondRoom: RoomResponse | undefined;
    await act(async () => {
      firstRoom = await result.current.mutateAsync(100);
    });
    await act(async () => {
      secondRoom = await result.current.mutateAsync(100);
    });

    expect(firstRoom?.id).toBe(7);
    expect(secondRoom?.id).toBe(7);
    expect(firstRoom?.id).toBe(secondRoom?.id);

    unmount();
    queryClient.clear();
  });
});

describe('useBackfill', () => {
  afterEach(() => jest.clearAllMocks());

  it('[U-05] afterMessageId 이후 메시지만 반환한다(경계값)', async () => {
    const afterOnly: MessageResponse[] = [
      { id: 12, roomId: 1, senderId: 3, content: '이후 메시지', sentAt: '2026-07-04T09:01:00Z' },
    ];
    backfillMessagesMock.mockResolvedValue(afterOnly);
    const { wrapper, queryClient } = createWrapper();

    const { result, unmount } = renderHook(() => useBackfill(1, 11), { wrapper });

    // 2단계 기능 — 자동 실행하지 않고 호출부(FE-06)가 명시적으로 refetch 트리거
    expect(result.current.isFetching).toBe(false);

    await act(async () => {
      await result.current.refetch();
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual(afterOnly);
    expect(result.current.data?.every((message) => message.id > 11)).toBe(true);
    expect(backfillMessagesMock).toHaveBeenCalledWith(1, 11);

    unmount();
    queryClient.clear();
  });
});

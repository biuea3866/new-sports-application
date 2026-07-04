/**
 * useChatSocket — STOMP 실시간 채팅 소켓 훅 검증.
 *
 * 근거: `20260704-채팅시스템고도화-design-fe-app.md` "방안 비교 — 실시간 상태"·"재연결/백필",
 *       티켓 `FE-06-chat-socket-hook.md` 테스트 케이스.
 *
 * `@stomp/stompjs`의 `Client`를 모의(Fake)로 대체해, 실제 WebSocket 연결 없이
 * onConnect/onWebSocketClose/subscribe 콜백을 테스트에서 직접 트리거한다.
 */
import { act, createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import type { BroadcastMessage, ChatMessage } from '../../api/chat-types';
import type { ListMessagesResponse, MessageResponse } from '../../api/types';
import type { FakeStompClientInstance } from './test-helpers/fake-stomp-client';

// @stomp/stompjs 모의 — 별도 모듈에서 require해 out-of-scope 변수 제약을 피한다.
jest.mock('@stomp/stompjs', () => require('./test-helpers/fake-stomp-client'));

// --- REST 폴백 대상 API 모의 ---
jest.mock('../../api/room', () => ({
  sendMessage: jest.fn(),
}));

import { sendMessage } from '../../api/room';
import { useAuthStore } from '../auth';
import { messagesQueryKey } from '../useRooms';
import { useChatSocket } from '../useChatSocket';
import { mockStompClientInstances } from './test-helpers/fake-stomp-client';

const sendMessageMock = sendMessage as jest.MockedFunction<typeof sendMessage>;

const ROOM_ID = 42;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

function latestClient(): FakeStompClientInstance {
  const instance = mockStompClientInstances[mockStompClientInstances.length - 1];
  if (!instance) {
    throw new Error('생성된 FakeStompClient 인스턴스가 없습니다');
  }
  return instance;
}

describe('useChatSocket', () => {
  const originalApiUrl = process.env.EXPO_PUBLIC_API_URL;
  const originalRealtimeFlag = process.env.EXPO_PUBLIC_CHAT_REALTIME_ENABLED;

  beforeEach(() => {
    process.env.EXPO_PUBLIC_API_URL = 'http://localhost:8080';
    delete process.env.EXPO_PUBLIC_CHAT_REALTIME_ENABLED;
    useAuthStore.getState().setAccessToken('access-token-abc');
    mockStompClientInstances.length = 0;
    sendMessageMock.mockReset();
  });

  afterEach(() => {
    process.env.EXPO_PUBLIC_API_URL = originalApiUrl;
    process.env.EXPO_PUBLIC_CHAT_REALTIME_ENABLED = originalRealtimeFlag;
    useAuthStore.getState().setAccessToken(null);
  });

  it('수신 BroadcastMessage를 ChatMessage로 정규화해 메시지 캐시에 append한다', async () => {
    const { wrapper, queryClient } = createWrapper();
    const { result, unmount } = renderHook(() => useChatSocket({ roomId: ROOM_ID }), { wrapper });

    await waitFor(() => expect(mockStompClientInstances).toHaveLength(1));
    const client = latestClient();

    act(() => {
      client.simulateConnect();
    });

    const broadcast: BroadcastMessage = {
      messageId: 100,
      userId: 7,
      content: '안녕하세요',
      createdAt: '2026-07-04T09:00:00Z',
    };

    act(() => {
      client.simulateMessage(`/topic/rooms/${ROOM_ID}`, broadcast);
    });

    await waitFor(() => expect(result.current.isConnected).toBe(true));

    const cached = queryClient.getQueryData<ListMessagesResponse>(messagesQueryKey(ROOM_ID));
    expect(cached?.messages).toHaveLength(1);
    expect(cached?.messages[0]).toEqual<ChatMessage>({
      id: 100,
      roomId: ROOM_ID,
      senderId: 7,
      content: '안녕하세요',
      sentAt: '2026-07-04T09:00:00Z',
    });

    unmount();
    queryClient.clear();
  });

  it('동일 messageId 중복 수신 시 캐시에 한 번만 존재한다(멱등 dedup)', async () => {
    const { wrapper, queryClient } = createWrapper();
    const { unmount } = renderHook(() => useChatSocket({ roomId: ROOM_ID }), { wrapper });

    await waitFor(() => expect(mockStompClientInstances).toHaveLength(1));
    const client = latestClient();

    act(() => {
      client.simulateConnect();
    });

    const broadcast: BroadcastMessage = {
      messageId: 200,
      userId: 7,
      content: '중복 메시지',
      createdAt: '2026-07-04T09:01:00Z',
    };

    act(() => {
      client.simulateMessage(`/topic/rooms/${ROOM_ID}`, broadcast);
      client.simulateMessage(`/topic/rooms/${ROOM_ID}`, broadcast);
    });

    await waitFor(() => {
      const cached = queryClient.getQueryData<ListMessagesResponse>(messagesQueryKey(ROOM_ID));
      expect(cached?.messages).toHaveLength(1);
    });

    unmount();
    queryClient.clear();
  });

  it('미연결 상태에서 send 호출 시 REST 폴백으로 전송한다', async () => {
    const mockResponse: MessageResponse = {
      id: 1,
      roomId: ROOM_ID,
      senderId: 1,
      content: '폴백 전송',
      sentAt: '2026-07-04T09:02:00Z',
    };
    sendMessageMock.mockResolvedValue(mockResponse);

    const { wrapper, queryClient } = createWrapper();
    const { result, unmount } = renderHook(() => useChatSocket({ roomId: ROOM_ID }), { wrapper });

    await waitFor(() => expect(mockStompClientInstances).toHaveLength(1));
    // onConnect를 트리거하지 않아 미연결 상태 유지

    await act(async () => {
      await result.current.send('폴백 전송');
    });

    expect(sendMessageMock).toHaveBeenCalledWith(ROOM_ID, { content: '폴백 전송' });
    expect(latestClient().publishedFrames).toHaveLength(0);

    unmount();
    queryClient.clear();
  });

  it('재연결 3회 연속 실패 시 pollingFallback이 true가 된다', async () => {
    const { wrapper, queryClient } = createWrapper();
    const { result, unmount } = renderHook(() => useChatSocket({ roomId: ROOM_ID }), { wrapper });

    await waitFor(() => expect(mockStompClientInstances).toHaveLength(1));
    const client = latestClient();

    act(() => {
      client.simulateClose();
    });
    expect(result.current.pollingFallback).toBe(false);

    act(() => {
      client.simulateClose();
    });
    expect(result.current.pollingFallback).toBe(false);

    act(() => {
      client.simulateClose();
    });

    await waitFor(() => expect(result.current.pollingFallback).toBe(true));

    unmount();
    queryClient.clear();
  });

  it('재연결 성공 시 마지막 수신 id 이후 구간을 backfill로 채운다', async () => {
    const backfilledMessage: ChatMessage = {
      id: 301,
      roomId: ROOM_ID,
      senderId: 9,
      content: '끊긴 동안 온 메시지',
      sentAt: '2026-07-04T09:05:00Z',
    };
    const backfillMock = jest.fn().mockResolvedValue([backfilledMessage]);

    const { wrapper, queryClient } = createWrapper();
    const { unmount } = renderHook(
      () => useChatSocket({ roomId: ROOM_ID, backfill: backfillMock }),
      { wrapper }
    );

    await waitFor(() => expect(mockStompClientInstances).toHaveLength(1));
    const client = latestClient();

    // 최초 연결 — backfill 대상 아님
    act(() => {
      client.simulateConnect();
    });

    const broadcast: BroadcastMessage = {
      messageId: 300,
      userId: 9,
      content: '연결 중 수신',
      createdAt: '2026-07-04T09:04:00Z',
    };
    act(() => {
      client.simulateMessage(`/topic/rooms/${ROOM_ID}`, broadcast);
    });

    expect(backfillMock).not.toHaveBeenCalled();

    // 연결 끊김 → 재연결(재구독) — backfill 대상
    act(() => {
      client.simulateClose();
    });
    act(() => {
      client.simulateConnect();
    });

    await waitFor(() => expect(backfillMock).toHaveBeenCalledWith(ROOM_ID, 300));

    await waitFor(() => {
      const cached = queryClient.getQueryData<ListMessagesResponse>(messagesQueryKey(ROOM_ID));
      expect(cached?.messages.map((message) => message.id)).toEqual([300, 301]);
    });

    unmount();
    queryClient.clear();
  });

  it('CONNECT 시 Authorization 헤더에 accessToken이 포함된다', async () => {
    const { wrapper, queryClient } = createWrapper();
    const { unmount } = renderHook(() => useChatSocket({ roomId: ROOM_ID }), { wrapper });

    await waitFor(() => expect(mockStompClientInstances).toHaveLength(1));
    const client = latestClient();

    expect(client.config.connectHeaders?.Authorization).toBe('Bearer access-token-abc');
    expect(client.activateCallCount).toBe(1);

    unmount();
    queryClient.clear();
  });

  it('플래그 OFF면 소켓 연결을 시도하지 않는다', async () => {
    process.env.EXPO_PUBLIC_CHAT_REALTIME_ENABLED = 'false';

    const { wrapper, queryClient } = createWrapper();
    const { result, unmount } = renderHook(() => useChatSocket({ roomId: ROOM_ID }), { wrapper });

    expect(mockStompClientInstances).toHaveLength(0);
    expect(result.current.isConnected).toBe(false);

    unmount();
    queryClient.clear();
  });
});

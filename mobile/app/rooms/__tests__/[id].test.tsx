/**
 * RoomChatScreen(S2) — 채팅방 실시간 화면 컨테이너 검증.
 * 근거: 티켓 `FE-10-room-chat-realtime-screen.md` 테스트 케이스.
 *
 * useMessages·useChatSocket·useMarkRead·useMyProfile을 모킹해 컨테이너의 배선(실시간 append,
 * 타이핑 인디케이터, 게스트 방출 403 폴백, 재연결 배너+폴링 폴백, 진입 시 markRead)만 검증한다.
 */
import React from 'react';
import { act, fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AxiosError } from 'axios';

import type { ListMessagesResponse, MessageResponse } from '../../../api/types';
import type { ReadEvent, TypingEvent } from '../../../api/chat-types';
import RoomChatScreen from '../[id]';

jest.mock('../../../lib/useRooms', () => ({
  useMessages: jest.fn(),
  messagesQueryKey: (roomId: number) => ['rooms', roomId, 'messages'],
}));
jest.mock('../../../lib/useChatSocket', () => ({
  useChatSocket: jest.fn(),
}));
jest.mock('../../../lib/useChat', () => ({
  useMarkRead: jest.fn(),
}));
jest.mock('../../../lib/useMyProfile', () => ({
  useMyProfile: jest.fn(),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useMessages } from '../../../lib/useRooms';
import { useChatSocket } from '../../../lib/useChatSocket';
import { useMarkRead } from '../../../lib/useChat';
import { useMyProfile } from '../../../lib/useMyProfile';

const useMessagesMock = useMessages as jest.MockedFunction<typeof useMessages>;
const useChatSocketMock = useChatSocket as jest.MockedFunction<typeof useChatSocket>;
const useMarkReadMock = useMarkRead as jest.MockedFunction<typeof useMarkRead>;
const useMyProfileMock = useMyProfile as jest.MockedFunction<typeof useMyProfile>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const MY_USER_ID = 1;
const OTHER_USER_ID = 2;

function buildMessages(messages: MessageResponse[]): ListMessagesResponse {
  return { messages, nextCursor: null };
}

function mockUseMessagesReturn(overrides: {
  data?: ListMessagesResponse;
  isLoading?: boolean;
  isError?: boolean;
  error?: unknown;
  refetch?: jest.Mock;
}) {
  useMessagesMock.mockReturnValue({
    data: overrides.data,
    isLoading: overrides.isLoading ?? false,
    isError: overrides.isError ?? false,
    error: overrides.error ?? null,
    refetch: overrides.refetch ?? jest.fn(),
  } as unknown as ReturnType<typeof useMessages>);
}

let capturedOnTyping: ((event: TypingEvent) => void) | undefined;
let capturedOnRead: ((event: ReadEvent) => void) | undefined;
let sendMock: jest.Mock;
let sendTypingMock: jest.Mock;

function mockUseChatSocketReturn(overrides: { isConnected?: boolean; pollingFallback?: boolean }) {
  useChatSocketMock.mockImplementation((options) => {
    capturedOnTyping = options.onTyping;
    capturedOnRead = options.onRead;
    return {
      isConnected: overrides.isConnected ?? true,
      pollingFallback: overrides.pollingFallback ?? false,
      send: sendMock,
      sendTyping: sendTypingMock,
      sendRead: jest.fn(),
    };
  });
}

describe('RoomChatScreen', () => {
  let markReadMutate: jest.Mock;
  let pushMock: jest.Mock;

  beforeEach(() => {
    jest.useFakeTimers();
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '10' });
    pushMock = jest.fn();
    useRouterMock.mockReturnValue({
      push: pushMock,
      replace: jest.fn(),
      back: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    useMyProfileMock.mockReturnValue({
      data: {
        id: MY_USER_ID,
        email: 'me@test.com',
        status: 'ACTIVE',
        createdAt: '2026-01-01T00:00:00Z',
      },
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useMyProfile>);
    markReadMutate = jest.fn();
    useMarkReadMock.mockReturnValue({
      mutate: markReadMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useMarkRead>);
    sendMock = jest.fn();
    sendTypingMock = jest.fn();
    capturedOnTyping = undefined;
    capturedOnRead = undefined;
    mockUseChatSocketReturn({});
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('내 메시지는 우측, 상대 메시지는 좌측 정렬로 렌더된다', () => {
    mockUseMessagesReturn({
      data: buildMessages([
        {
          id: 1,
          roomId: 10,
          senderId: OTHER_USER_ID,
          content: '안녕하세요',
          sentAt: '2026-07-06T00:00:00Z',
        },
        {
          id: 2,
          roomId: 10,
          senderId: MY_USER_ID,
          content: '네 안녕하세요',
          sentAt: '2026-07-06T00:01:00Z',
        },
      ]),
    });

    render(<RoomChatScreen />);

    const rows = screen.getAllByTestId('message-bubble-row');
    expect(rows).toHaveLength(2);
    expect(screen.getByText('안녕하세요')).toBeTruthy();
    expect(screen.getByText('네 안녕하세요')).toBeTruthy();
  });

  it('실시간 수신 메시지가 리스트에 append되어 표시된다', () => {
    mockUseMessagesReturn({
      data: buildMessages([
        {
          id: 1,
          roomId: 10,
          senderId: OTHER_USER_ID,
          content: '첫 메시지',
          sentAt: '2026-07-06T00:00:00Z',
        },
      ]),
    });

    const { rerender } = render(<RoomChatScreen />);
    expect(screen.getByText('첫 메시지')).toBeTruthy();

    mockUseMessagesReturn({
      data: buildMessages([
        {
          id: 1,
          roomId: 10,
          senderId: OTHER_USER_ID,
          content: '첫 메시지',
          sentAt: '2026-07-06T00:00:00Z',
        },
        {
          id: 2,
          roomId: 10,
          senderId: OTHER_USER_ID,
          content: '실시간 수신 메시지',
          sentAt: '2026-07-06T00:02:00Z',
        },
      ]),
    });
    rerender(<RoomChatScreen />);

    expect(screen.getByText('실시간 수신 메시지')).toBeTruthy();
  });

  it('상대 타이핑 이벤트 수신 시 인디케이터가 표시되고 3초 후 사라진다', () => {
    mockUseMessagesReturn({ data: buildMessages([]) });

    render(<RoomChatScreen />);

    act(() => {
      capturedOnTyping?.({ userId: OTHER_USER_ID, typing: true });
    });
    expect(screen.getByText('상대가 입력 중…')).toBeTruthy();

    act(() => {
      jest.advanceTimersByTime(3000);
    });
    expect(screen.queryByText('상대가 입력 중…')).toBeNull();
  });

  it('게스트 방출(403) 시 만료 안내 전체화면과 목록으로가 렌더된다', () => {
    const forbiddenError = new AxiosError('Forbidden', undefined, undefined, undefined, {
      status: 403,
      data: {},
      statusText: 'Forbidden',
      headers: {},
      config: {} as never,
    });
    mockUseMessagesReturn({ isError: true, error: forbiddenError });

    render(<RoomChatScreen />);

    expect(screen.getByText('참여 기간이 만료되어 대화를 볼 수 없어요')).toBeTruthy();
    fireEvent.press(screen.getByRole('button', { name: '목록으로' }));
    expect(pushMock).toHaveBeenCalledWith('/rooms');
  });

  it('연결 끊김 시 재연결 배너가 표시되고 폴링 폴백이면 주기적으로 refetch한다', () => {
    const refetchMock = jest.fn();
    mockUseMessagesReturn({ data: buildMessages([]), refetch: refetchMock });
    mockUseChatSocketReturn({ isConnected: false, pollingFallback: true });

    render(<RoomChatScreen />);

    expect(screen.getByText('실시간 연결이 어려워 새로고침으로 갱신하고 있어요')).toBeTruthy();

    act(() => {
      jest.advanceTimersByTime(5000);
    });
    expect(refetchMock).toHaveBeenCalled();
  });

  it('방 진입 시 markRead가 마지막 메시지 id로 호출된다', () => {
    mockUseMessagesReturn({
      data: buildMessages([
        {
          id: 1,
          roomId: 10,
          senderId: OTHER_USER_ID,
          content: '메시지1',
          sentAt: '2026-07-06T00:00:00Z',
        },
        {
          id: 42,
          roomId: 10,
          senderId: OTHER_USER_ID,
          content: '메시지2',
          sentAt: '2026-07-06T00:01:00Z',
        },
      ]),
    });

    render(<RoomChatScreen />);

    expect(markReadMutate).toHaveBeenCalledWith({ roomId: 10, lastReadMessageId: 42 });
  });

  it('메시지가 없으면 첫 메시지를 보내보세요 안내가 렌더된다', () => {
    mockUseMessagesReturn({ data: buildMessages([]) });

    render(<RoomChatScreen />);

    expect(screen.getByText('첫 메시지를 보내보세요')).toBeTruthy();
  });

  it('로딩 중이면 로딩 표시를 렌더한다', () => {
    mockUseMessagesReturn({ isLoading: true });

    render(<RoomChatScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('403이 아닌 오류는 ErrorView와 재시도를 렌더한다', () => {
    const serverError = new AxiosError('Server Error', undefined, undefined, undefined, {
      status: 500,
      data: {},
      statusText: 'Internal Server Error',
      headers: {},
      config: {} as never,
    });
    const refetchMock = jest.fn();
    mockUseMessagesReturn({ isError: true, error: serverError, refetch: refetchMock });

    render(<RoomChatScreen />);

    fireEvent.press(screen.getByLabelText('다시 시도'));
    expect(refetchMock).toHaveBeenCalled();
  });

  it('onRead 이벤트 수신 시 내 메시지에 읽음 표시가 반영된다', () => {
    mockUseMessagesReturn({
      data: buildMessages([
        {
          id: 1,
          roomId: 10,
          senderId: MY_USER_ID,
          content: '내가 보낸 메시지',
          sentAt: '2026-07-06T00:00:00Z',
        },
      ]),
    });

    render(<RoomChatScreen />);
    expect(screen.queryByText('읽음')).toBeNull();

    act(() => {
      capturedOnRead?.({ userId: OTHER_USER_ID, lastReadMessageId: 1 });
    });

    expect(screen.getByText('읽음')).toBeTruthy();
  });
});

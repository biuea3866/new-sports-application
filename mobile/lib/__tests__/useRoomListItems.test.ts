/**
 * useRoomListItems — 방 목록 화면(S1) 컨테이너 병합 로직 (useRooms + useUnreadCounts)
 *
 * 근거: FE-09 티켓 "테스트 케이스", design-fe-app.md S1 상태 표
 * ("실패 시 배지 0 폴백(비차단)").
 *
 * useRooms/useUnreadCounts를 모킹해 병합 결과(표시 이름·미리보기·시각·안읽은 수)와
 * loading/error 판단 기준(방 목록 조회 기준)만 검증한다.
 */
import { renderHook } from '@testing-library/react-native';

jest.mock('../useRooms', () => ({
  useRooms: jest.fn(),
}));

jest.mock('../useChat', () => ({
  useUnreadCounts: jest.fn(),
}));

import { useRooms } from '../useRooms';
import { useUnreadCounts } from '../useChat';
import { mapRoomsToListItems, useRoomListItems } from '../useRoomListItems';
import type { RoomResponse } from '../../api/types';
import type { RoomUnreadResponse } from '../../api/chat-types';

const useRoomsMock = useRooms as jest.MockedFunction<typeof useRooms>;
const useUnreadCountsMock = useUnreadCounts as jest.MockedFunction<typeof useUnreadCounts>;

function mockRoomsReturn(overrides: Partial<ReturnType<typeof useRooms>>): void {
  useRoomsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    isRefetching: false,
    ...overrides,
  } as unknown as ReturnType<typeof useRooms>);
}

function mockUnreadReturn(overrides: Partial<ReturnType<typeof useUnreadCounts>>): void {
  useUnreadCountsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useUnreadCounts>);
}

const communityRoom: RoomResponse = {
  id: 1,
  type: 'GROUP',
  name: '주말 축구 모임',
  contextType: 'COMMUNITY',
  lastMessagePreview: '오늘 몇 시에 모여요?',
  lastMessageAt: '2026-07-05T14:20:00+09:00',
};

const directRoomWithoutName: RoomResponse = {
  id: 2,
  type: 'DIRECT',
  name: null,
};

describe('mapRoomsToListItems', () => {
  it('안읽은 수 목록과 roomId로 병합해 배지 수를 채운다', () => {
    const unreadCounts: RoomUnreadResponse[] = [{ roomId: 1, unreadCount: 3 }];

    const items = mapRoomsToListItems([communityRoom], unreadCounts);

    expect(items).toEqual([
      {
        id: 1,
        displayName: '주말 축구 모임',
        previewText: '오늘 몇 시에 모여요?',
        timeLabel: '14:20',
        unreadCount: 3,
      },
    ]);
  });

  it('안읽은 수 목록에 없는 방은 안읽은 수가 0으로 채워진다', () => {
    const items = mapRoomsToListItems([communityRoom], []);

    expect(items[0].unreadCount).toBe(0);
  });

  it('이름이 없는 DIRECT 방은 "1:1 채팅"으로, 미리보기·시각이 없으면 null로 채워진다', () => {
    const items = mapRoomsToListItems([directRoomWithoutName], []);

    expect(items[0].displayName).toBe('1:1 채팅');
    expect(items[0].previewText).toBeNull();
    expect(items[0].timeLabel).toBeNull();
  });
});

describe('useRoomListItems', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('방 목록 조회가 로딩 중이면 isLoading이 true다', () => {
    mockRoomsReturn({ isLoading: true, data: undefined });
    mockUnreadReturn({ data: undefined });

    const { result } = renderHook(() => useRoomListItems());

    expect(result.current.isLoading).toBe(true);
  });

  it('방 목록 조회가 실패하면 isError가 true다', () => {
    mockRoomsReturn({ isError: true, data: undefined });
    mockUnreadReturn({ data: undefined });

    const { result } = renderHook(() => useRoomListItems());

    expect(result.current.isError).toBe(true);
  });

  it('안읽은 수 조회가 실패해도 방 목록 조회가 성공이면 에러 상태가 아니고 배지는 0으로 폴백된다', () => {
    mockRoomsReturn({ data: [communityRoom] });
    mockUnreadReturn({ isError: true, data: undefined });

    const { result } = renderHook(() => useRoomListItems());

    expect(result.current.isError).toBe(false);
    expect(result.current.items[0].unreadCount).toBe(0);
  });

  it('refetch 호출 시 방 목록과 안읽은 수 조회를 모두 재요청한다', () => {
    const refetchRooms = jest.fn();
    const refetchUnread = jest.fn();
    mockRoomsReturn({ data: [communityRoom], refetch: refetchRooms });
    mockUnreadReturn({ data: [], refetch: refetchUnread });

    const { result } = renderHook(() => useRoomListItems());
    result.current.refetch();

    expect(refetchRooms).toHaveBeenCalled();
    expect(refetchUnread).toHaveBeenCalled();
  });
});

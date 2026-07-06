/**
 * RoomsListScreen(S1) — 안읽은 배지·빈 상태·에러 재시도·탭 이동·다크 모드 검증
 * 근거: FE-09 티켓 "테스트 케이스", design-fe-app.md S1 상태 표.
 *
 * useRoomListItems(병합 훅)·useMyInvitations을 모킹해 화면의 렌더링 분기만
 * 사용자 관점(보이는 텍스트·role·인터랙션)으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

jest.mock('../../../lib/useRoomListItems', () => ({
  useRoomListItems: jest.fn(),
}));

jest.mock('../../../lib/useInvitations', () => ({
  useMyInvitations: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: {
    push: jest.fn(),
  },
}));

import { router } from 'expo-router';
import { useRoomListItems } from '../../../lib/useRoomListItems';
import { useMyInvitations } from '../../../lib/useInvitations';
import { darkTokens } from '../../../theme/tokens';
import type { InvitationResponse } from '../../../api/chat-types';
import RoomsListScreen from '../index';

const PENDING_INVITATION: InvitationResponse = {
  id: 1,
  roomId: 7,
  inviterUserId: 10,
  inviteeUserId: 20,
  status: 'PENDING',
  canSpeak: true,
  expiresAt: '2026-07-12T00:00:00+09:00',
  createdAt: '2026-07-05T00:00:00+09:00',
};

const useRoomListItemsMock = useRoomListItems as jest.MockedFunction<typeof useRoomListItems>;
const useMyInvitationsMock = useMyInvitations as jest.MockedFunction<typeof useMyInvitations>;
const pushMock = router.push as jest.Mock;

function mockRoomListItemsReturn(overrides: Partial<ReturnType<typeof useRoomListItems>>): void {
  useRoomListItemsMock.mockReturnValue({
    items: [],
    isLoading: false,
    isError: false,
    isRefreshing: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useRoomListItems>);
}

function mockInvitationsReturn(overrides: Partial<ReturnType<typeof useMyInvitations>>): void {
  useMyInvitationsMock.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    ...overrides,
  } as unknown as ReturnType<typeof useMyInvitations>);
}

describe('RoomsListScreen', () => {
  beforeEach(() => {
    mockInvitationsReturn({ data: [] });
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
    delete process.env.EXPO_PUBLIC_CHAT_COMMUNITY_ENABLED;
  });

  it('chat.community.enabled 플래그가 OFF면 초대함 버튼이 렌더되지 않는다', () => {
    process.env.EXPO_PUBLIC_CHAT_COMMUNITY_ENABLED = 'false';
    mockRoomListItemsReturn({ items: [] });

    render(<RoomsListScreen />);

    expect(screen.queryByLabelText('초대함')).toBeNull();
    expect(screen.queryByLabelText('초대함, 대기 중인 초대 있음')).toBeNull();
  });

  it('chat.community.enabled 플래그가 기본값(ON)이면 초대함 버튼이 렌더된다', () => {
    mockRoomListItemsReturn({ items: [] });

    render(<RoomsListScreen />);

    expect(screen.getByLabelText('초대함')).toBeTruthy();
  });

  it('안읽은 수>0인 방에는 배지 숫자가, 0인 방에는 배지가 렌더되지 않는다', () => {
    mockRoomListItemsReturn({
      items: [
        {
          id: 1,
          displayName: '주말 축구 모임',
          previewText: '오늘 몇 시에 모여요?',
          timeLabel: '14:20',
          unreadCount: 3,
        },
        {
          id: 2,
          displayName: '이영희',
          previewText: '안녕하세요 상품 문의드려요',
          timeLabel: '12:05',
          unreadCount: 0,
        },
      ],
    });

    render(<RoomsListScreen />);

    expect(screen.getByLabelText('안읽은 메시지 3개')).toBeTruthy();
    expect(screen.queryByLabelText('안읽은 메시지 0개')).toBeNull();
  });

  it('방 목록이 비면 빈 상태 문구가 렌더된다', () => {
    mockRoomListItemsReturn({ items: [] });

    render(<RoomsListScreen />);

    expect(screen.getByText('참여 중인 채팅방이 없어요')).toBeTruthy();
  });

  it('로드 실패 시 ErrorView와 재시도 버튼이 렌더되고 재시도가 refetch를 부른다', () => {
    const refetchMock = jest.fn();
    mockRoomListItemsReturn({ isError: true, refetch: refetchMock });

    render(<RoomsListScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetchMock).toHaveBeenCalled();
  });

  it('방 아이템 탭 시 /rooms/{id}로 이동한다', () => {
    mockRoomListItemsReturn({
      items: [
        {
          id: 7,
          displayName: '주말 축구 모임',
          previewText: null,
          timeLabel: '14:20',
          unreadCount: 0,
        },
      ],
    });

    render(<RoomsListScreen />);
    fireEvent.press(screen.getByLabelText('주말 축구 모임'));

    expect(pushMock).toHaveBeenCalledWith('/rooms/7');
  });

  it('다크 모드에서 배경·텍스트가 다크 토큰으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockRoomListItemsReturn({
      items: [
        {
          id: 1,
          displayName: '주말 축구 모임',
          previewText: null,
          timeLabel: '14:20',
          unreadCount: 0,
        },
      ],
    });

    render(<RoomsListScreen />);

    expect(screen.getByTestId('rooms-list-screen')).toHaveStyle({
      backgroundColor: darkTokens.background,
    });
    expect(screen.getByText('채팅')).toHaveStyle({ color: darkTokens.textPrimary });
  });

  it('로딩 중이면 스켈레톤 로딩 뷰가 렌더된다', () => {
    mockRoomListItemsReturn({ isLoading: true });

    render(<RoomsListScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('대기 중인 초대가 있으면 초대함 버튼에 안내 라벨이 붙는다', () => {
    mockInvitationsReturn({ data: [PENDING_INVITATION] });
    mockRoomListItemsReturn({ items: [] });

    render(<RoomsListScreen />);

    expect(screen.getByLabelText('초대함, 대기 중인 초대 있음')).toBeTruthy();
  });

  it('초대함 버튼 탭 시 /invitations로 이동한다', () => {
    mockRoomListItemsReturn({ items: [] });

    render(<RoomsListScreen />);
    fireEvent.press(screen.getByLabelText('초대함'));

    expect(pushMock).toHaveBeenCalledWith('/invitations');
  });
});

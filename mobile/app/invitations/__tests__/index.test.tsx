/**
 * MyInvitationsScreen(S7) — 초대 수신함 4상태·수락/거절 배선 검증.
 * 근거: design-fe-app.md S7, tickets/FE-13 테스트 케이스.
 *
 * useMyInvitations/useAcceptInvitation/useRejectInvitation(FE-08)을 모킹해
 * 화면 배선(빈 상태·수락 성공 시 이동·거절 성공 시 카드 제거·상태 보호 안내)만 검증한다.
 * 방목록·수신함 캐시 무효화는 훅 자체 책임이며 useInvitations 훅 테스트에서 이미 검증한다.
 */
import React from 'react';
import { render, screen, fireEvent, within } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { useRouter } from 'expo-router';

import MyInvitationsScreen from '../index';
import {
  useAcceptInvitation,
  useMyInvitations,
  useRejectInvitation,
} from '../../../lib/useInvitations';
import { darkTokens } from '../../../theme/tokens';
import type { InvitationResponse } from '../../../api/chat-types';

jest.mock('../../../lib/useInvitations', () => ({
  useMyInvitations: jest.fn(),
  useAcceptInvitation: jest.fn(),
  useRejectInvitation: jest.fn(),
}));

const useMyInvitationsMock = useMyInvitations as jest.MockedFunction<typeof useMyInvitations>;
const useAcceptInvitationMock = useAcceptInvitation as jest.MockedFunction<
  typeof useAcceptInvitation
>;
const useRejectInvitationMock = useRejectInvitation as jest.MockedFunction<
  typeof useRejectInvitation
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const mockPush = jest.fn();
const mockAcceptMutate = jest.fn();
const mockRejectMutate = jest.fn();

function mockMyInvitations(overrides: Partial<ReturnType<typeof useMyInvitations>> = {}): void {
  useMyInvitationsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useMyInvitations>);
}

function buildInvitation(overrides: Partial<InvitationResponse> = {}): InvitationResponse {
  return {
    id: 1,
    roomId: 10,
    inviterUserId: 7,
    inviteeUserId: 99,
    status: 'PENDING',
    canSpeak: true,
    expiresAt: new Date(Date.now() + 7 * 86_400_000).toISOString(),
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

describe('MyInvitationsScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({
      back: jest.fn(),
      push: mockPush,
      replace: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    useAcceptInvitationMock.mockReturnValue({
      mutate: mockAcceptMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useAcceptInvitation>);
    useRejectInvitationMock.mockReturnValue({
      mutate: mockRejectMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useRejectInvitation>);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('수신함이 비면 빈 상태 문구가 렌더된다', () => {
    mockMyInvitations({ data: [] });

    render(<MyInvitationsScreen />);

    expect(screen.getByText('받은 초대가 없어요')).toBeTruthy();
  });

  it('초대 수락 시 방으로 이동한다(캐시 무효화는 훅 책임)', () => {
    mockMyInvitations({ data: [buildInvitation({ id: 1, roomId: 55 })] });
    mockAcceptMutate.mockImplementation((_id, options) => {
      options.onSuccess(buildInvitation({ id: 1, roomId: 55 }));
    });

    render(<MyInvitationsScreen />);
    const card = screen.getByTestId('invitation-card-1');
    fireEvent.press(within(card).getByRole('button', { name: '수락' }));

    expect(mockAcceptMutate).toHaveBeenCalledWith(1, expect.any(Object));
    expect(mockPush).toHaveBeenCalledWith('/rooms/55');
  });

  it('초대 거절 시 해당 카드가 목록에서 제거된다', () => {
    mockMyInvitations({
      data: [buildInvitation({ id: 1, roomId: 10 }), buildInvitation({ id: 2, roomId: 20 })],
    });
    mockRejectMutate.mockImplementation((_id, options) => {
      options.onSuccess(buildInvitation({ id: 1, roomId: 10, status: 'REJECTED' }));
    });

    render(<MyInvitationsScreen />);
    const cardToReject = screen.getByTestId('invitation-card-1');
    fireEvent.press(within(cardToReject).getByRole('button', { name: '거절' }));

    expect(screen.queryByTestId('invitation-card-1')).toBeNull();
    expect(screen.getByTestId('invitation-card-2')).toBeTruthy();
  });

  it('다크 모드에서 화면 루트가 다크 토큰 배경 값으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockMyInvitations({ data: [] });

    render(<MyInvitationsScreen />);

    expect(screen.getByTestId('invitations-screen-root')).toHaveStyle({
      backgroundColor: darkTokens.background,
    });
  });

  it('이미 만료된 초대 수락 시 서버 거부 안내가 표시된다', () => {
    mockMyInvitations({ data: [buildInvitation({ id: 1, roomId: 10 })] });
    mockAcceptMutate.mockImplementation((_id, options) => {
      options.onError(new Error('conflict'));
    });

    render(<MyInvitationsScreen />);
    const card = screen.getByTestId('invitation-card-1');
    fireEvent.press(within(card).getByRole('button', { name: '수락' }));

    expect(within(card).getByText('이미 만료되었거나 처리된 초대예요')).toBeTruthy();
    expect(mockPush).not.toHaveBeenCalled();
    expect(screen.getByTestId('invitation-card-1')).toBeTruthy();
  });
});

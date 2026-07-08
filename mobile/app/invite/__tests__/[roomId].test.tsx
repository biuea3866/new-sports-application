/**
 * InviteGuestScreen(S6) — 초대 발송 폼 계약 전송·멱등 안내 검증.
 * 근거: design-fe-app.md S6, tickets/FE-13 테스트 케이스.
 *
 * useInviteGuest(FE-08)를 모킹해 화면 배선(폼 → mutate 페이로드, 응답 처리 분기)만 검증한다.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { useLocalSearchParams, useRouter } from 'expo-router';

import InviteGuestScreen from '../[roomId]';
import { useInviteGuest } from '../../../lib/useInvitations';
import type { InvitationResponse } from '../../../api/chat-types';

jest.mock('../../../lib/useInvitations', () => ({
  useInviteGuest: jest.fn(),
}));

const useInviteGuestMock = useInviteGuest as jest.MockedFunction<typeof useInviteGuest>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const mockBack = jest.fn();
const mockMutate = jest.fn();

function mockInviteGuest(overrides: Partial<ReturnType<typeof useInviteGuest>> = {}): void {
  useInviteGuestMock.mockReturnValue({
    mutate: mockMutate,
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useInviteGuest>);
}

function buildInvitationResponse(overrides: Partial<InvitationResponse> = {}): InvitationResponse {
  return {
    id: 1,
    roomId: 42,
    inviterUserId: 7,
    inviteeUserId: 99,
    status: 'PENDING',
    canSpeak: true,
    expiresAt: new Date(Date.now() + 7 * 86_400_000).toISOString(),
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

describe('InviteGuestScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ roomId: '42' });
    useRouterMock.mockReturnValue({
      back: mockBack,
      push: jest.fn(),
      replace: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    mockInviteGuest();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('초대 발송 폼이 발화권한·참여기간을 계약대로 전송한다', () => {
    render(<InviteGuestScreen />);

    fireEvent.changeText(screen.getByLabelText('대상 사용자 ID 입력'), '99');
    fireEvent.press(screen.getByRole('button', { name: '읽기 전용' }));
    fireEvent.changeText(screen.getByLabelText('참여 기간 일수 입력'), '14');
    fireEvent.press(screen.getByRole('button', { name: '초대 보내기' }));

    expect(mockMutate).toHaveBeenCalledWith(
      {
        roomId: 42,
        request: { inviteeUserId: 99, canSpeak: false, expiresInDays: 14 },
      },
      expect.objectContaining({ onSuccess: expect.any(Function), onError: expect.any(Function) })
    );
  });

  it('이미 대기 중 초대가 있으면 멱등 안내가 인라인으로 표시되고 방으로 복귀하지 않는다', () => {
    mockMutate.mockImplementation((_variables, options) => {
      const reusedResponse = buildInvitationResponse({
        createdAt: new Date(Date.now() - 60_000).toISOString(),
      });
      options.onSuccess(reusedResponse);
    });

    render(<InviteGuestScreen />);
    fireEvent.changeText(screen.getByLabelText('대상 사용자 ID 입력'), '99');
    fireEvent.press(screen.getByRole('button', { name: '초대 보내기' }));

    expect(screen.getByText('이미 대기 중인 초대가 있어요')).toBeTruthy();
    expect(mockBack).not.toHaveBeenCalled();
  });

  it('신규 초대가 생성되면 방으로 복귀한다', () => {
    mockMutate.mockImplementation((_variables, options) => {
      options.onSuccess(buildInvitationResponse());
    });

    render(<InviteGuestScreen />);
    fireEvent.changeText(screen.getByLabelText('대상 사용자 ID 입력'), '99');
    fireEvent.press(screen.getByRole('button', { name: '초대 보내기' }));

    expect(mockBack).toHaveBeenCalledTimes(1);
  });
});

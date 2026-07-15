/**
 * ChatEntryButton — 채팅 목록(/rooms) 진입 아이콘 버튼 사용자 관점 동작 검증.
 * 근거: 사용자 피드백 "채팅은 탭에서 제거 → 홈·커뮤니티 화면 상단 우측 아이콘으로 진입,
 * 기존 전역 안읽은 수 배지를 그 아이콘에 표시".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

jest.mock('../../../lib/useTotalUnread', () => ({
  useTotalUnread: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
}));

import { useRouter } from 'expo-router';
import { useTotalUnread } from '../../../lib/useTotalUnread';
import { ChatEntryButton } from '../ChatEntryButton';

const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const useTotalUnreadMock = useTotalUnread as jest.MockedFunction<typeof useTotalUnread>;

describe('ChatEntryButton', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({ push: pushMock } as unknown as ReturnType<typeof useRouter>);
    useTotalUnreadMock.mockReturnValue(0);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('탭하면 채팅방 목록(/rooms)으로 이동한다', () => {
    render(<ChatEntryButton />);
    fireEvent.press(screen.getByLabelText('채팅'));

    expect(pushMock).toHaveBeenCalledWith('/rooms');
  });

  it('안읽은 수가 0이면 배지를 표시하지 않는다', () => {
    useTotalUnreadMock.mockReturnValue(0);

    render(<ChatEntryButton />);

    expect(screen.queryByText('0')).toBeNull();
  });

  it('안읽은 수가 0보다 크면 배지와 접근성 라벨에 개수를 표시한다', () => {
    useTotalUnreadMock.mockReturnValue(5);

    render(<ChatEntryButton />);

    expect(screen.getByText('5')).toBeTruthy();
    expect(screen.getByLabelText('채팅, 안읽은 메시지 5개')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    useTotalUnreadMock.mockReturnValue(2);

    render(<ChatEntryButton />);

    expect(screen.getByLabelText('채팅, 안읽은 메시지 2개')).toBeTruthy();
  });
});

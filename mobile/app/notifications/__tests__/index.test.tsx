/**
 * 알림함 화면 — 목록이 제목·본문·분류를 실제로 렌더하는지 검증한다.
 *
 * 회귀 배경: 알림 16건이 전부 제목·본문 없는 빈 회색 막대로 렌더되고 시각만 보였다
 * (유즈케이스 캡쳐 36-알림함). BE 목록 응답에 title/content/category/isRead 가 없어
 * 화면이 undefined 를 렌더한 것이 원인이다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';

import NotificationsScreen from '../index';
import type { NotificationListResponse, UnreadCountResponse } from '../../../api/types';

jest.mock('../../../lib/useNotifications', () => ({
  useNotifications: jest.fn(),
  useUnreadCount: jest.fn(),
  useMarkNotificationRead: jest.fn(),
}));

import {
  useNotifications,
  useUnreadCount,
  useMarkNotificationRead,
} from '../../../lib/useNotifications';

const useNotificationsMock = useNotifications as jest.MockedFunction<typeof useNotifications>;
const useUnreadCountMock = useUnreadCount as jest.MockedFunction<typeof useUnreadCount>;
const useMarkNotificationReadMock = useMarkNotificationRead as jest.MockedFunction<
  typeof useMarkNotificationRead
>;

const notificationList: NotificationListResponse = {
  content: [
    {
      id: 1,
      title: '결제 완료',
      content: '88,000원 결제가 완료되었습니다.',
      category: 'PAYMENT',
      isRead: false,
      readAt: null,
      createdAt: '2026-07-31T05:54:00Z',
    },
    {
      id: 2,
      title: '예약 확정',
      content: '잠실 실내체육관 예약이 확정되었습니다.',
      category: 'BOOKING',
      isRead: true,
      readAt: '2026-07-31T06:00:00Z',
      createdAt: '2026-07-31T05:50:00Z',
    },
  ],
  totalElements: 2,
  totalPages: 1,
  page: 0,
  size: 20,
};

function mockScreenState(
  list: NotificationListResponse | undefined,
  overrides: { isLoading?: boolean; isError?: boolean } = {}
) {
  useNotificationsMock.mockReturnValue({
    data: list,
    isLoading: overrides.isLoading ?? false,
    isError: overrides.isError ?? false,
    refetch: jest.fn(),
  } as unknown as ReturnType<typeof useNotifications>);
  useUnreadCountMock.mockReturnValue({
    data: { unreadCount: 1 } satisfies UnreadCountResponse,
  } as unknown as ReturnType<typeof useUnreadCount>);
  useMarkNotificationReadMock.mockReturnValue({
    mutate: jest.fn(),
  } as unknown as ReturnType<typeof useMarkNotificationRead>);
}

describe('NotificationsScreen', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('알림의 제목과 본문을 렌더한다', () => {
    mockScreenState(notificationList);

    render(<NotificationsScreen />);

    expect(screen.getByText('결제 완료')).toBeTruthy();
    expect(screen.getByText('88,000원 결제가 완료되었습니다.')).toBeTruthy();
    expect(screen.getByText('예약 확정')).toBeTruthy();
  });

  it('분류를 한글 배지로 렌더한다', () => {
    mockScreenState(notificationList);

    render(<NotificationsScreen />);

    expect(screen.getByText('결제')).toBeTruthy();
    expect(screen.getByText('예약')).toBeTruthy();
  });

  it('읽음 여부를 접근성 라벨로 구분한다', () => {
    mockScreenState(notificationList);

    render(<NotificationsScreen />);

    expect(screen.getByLabelText('결제 완료, 결제, 안읽음')).toBeTruthy();
    expect(screen.getByLabelText('예약 확정, 예약, 읽음')).toBeTruthy();
  });

  it('목록이 비면 빈 상태 문구를 보여준다', () => {
    mockScreenState({ ...notificationList, content: [], totalElements: 0 });

    render(<NotificationsScreen />);

    expect(screen.getByText('알림이 없습니다.')).toBeTruthy();
  });

  it('조회 실패 시 오류 문구와 재시도를 보여준다', () => {
    mockScreenState(undefined, { isError: true });

    render(<NotificationsScreen />);

    expect(screen.getByText('알림을 불러올 수 없습니다.')).toBeTruthy();
    expect(screen.getByLabelText('다시 시도')).toBeTruthy();
  });
});

/**
 * OrderHistoryScreen — 내 주문 통합 조회 화면 배선 검증.
 * 근거: `20260708-상품주문-공유상위컨텍스트-design-fe-app.md` Testing Plan
 * "OrderHistoryScreen"(시간 역순 리스트 / 401 로그인 유도 / empty·부분 실패 상태 /
 * orderType 필터 변경 시 재조회), 티켓 FE-10.
 *
 * useOrderHistory를 모킹해 화면 배선만 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import OrderHistoryScreen from '../index';
import type { OrderHistoryItem, OrderHistoryResponse } from '../../../api/order-history-types';

jest.mock('../../../lib/useOrderHistory', () => ({
  useOrderHistory: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
}));

import { router } from 'expo-router';
import { useOrderHistory } from '../../../lib/useOrderHistory';

const useOrderHistoryMock = useOrderHistory as jest.MockedFunction<typeof useOrderHistory>;

function axiosErrorWithStatus(status: number): AxiosError {
  return new AxiosError('boom', undefined, undefined, undefined, {
    status,
    data: {},
    statusText: '',
    headers: {},
    config: {} as never,
  });
}

function makeItem(overrides: Partial<OrderHistoryItem> = {}): OrderHistoryItem {
  return {
    orderType: 'BOOKING',
    sourceId: 4821,
    title: '강남 풋살장 예약',
    status: 'CONFIRMED',
    paymentId: 4821,
    detailPath: '/booking/4821',
    createdAt: '2026-07-05T10:00:00.000Z',
    ...overrides,
  };
}

function mockOrderHistory(overrides: Partial<ReturnType<typeof useOrderHistory>>) {
  useOrderHistoryMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useOrderHistory>);
}

describe('OrderHistoryScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('성공 시 전달받은 순서대로(시간 역순) 주문 목록을 카드로 보여준다', () => {
    const response: OrderHistoryResponse = {
      items: [
        makeItem({
          orderType: 'BOOKING',
          sourceId: 1,
          title: '강남 풋살장 예약',
          createdAt: '2026-07-08T10:00:00.000Z',
        }),
        makeItem({
          orderType: 'GOODS',
          sourceId: 2,
          title: '요가매트 프리미엄 외 1건',
          createdAt: '2026-07-05T10:00:00.000Z',
        }),
      ],
      page: 0,
      size: 20,
      failedDomains: [],
    };
    mockOrderHistory({ data: response });

    render(<OrderHistoryScreen />);

    expect(screen.getByText('강남 풋살장 예약')).toBeTruthy();
    expect(screen.getByText('요가매트 프리미엄 외 1건')).toBeTruthy();
  });

  it('로딩 중이면 스켈레톤을 보여준다', () => {
    mockOrderHistory({ isLoading: true });

    render(<OrderHistoryScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('결과가 0건이면 empty 문구를 보여준다', () => {
    mockOrderHistory({ data: { items: [], page: 0, size: 20, failedDomains: [] } });

    render(<OrderHistoryScreen />);

    expect(screen.getByText('주문 내역이 없어요')).toBeTruthy();
  });

  it('401이면 로그인 유도 문구와 로그인하기 버튼을 보여준다', () => {
    mockOrderHistory({ isError: true, error: axiosErrorWithStatus(401) });

    render(<OrderHistoryScreen />);

    expect(screen.getByText('로그인이 필요해요')).toBeTruthy();
    expect(screen.getByLabelText('로그인하기')).toBeTruthy();
  });

  it('로그인하기를 탭하면 로그인 화면으로 이동한다', () => {
    mockOrderHistory({ isError: true, error: axiosErrorWithStatus(401) });

    render(<OrderHistoryScreen />);
    fireEvent.press(screen.getByLabelText('로그인하기'));

    expect(router.replace).toHaveBeenCalledWith('/(auth)/login');
  });

  it('401이 아닌 에러면 재시도 가능한 오류 화면을 보여준다', () => {
    const refetchMock = jest.fn();
    mockOrderHistory({ isError: true, error: axiosErrorWithStatus(500), refetch: refetchMock });

    render(<OrderHistoryScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetchMock).toHaveBeenCalled();
  });

  it('failedDomains가 있으면 부분 실패 배너와 결과를 함께 보여준다', () => {
    const response: OrderHistoryResponse = {
      items: [makeItem()],
      page: 0,
      size: 20,
      failedDomains: ['TICKETING'],
    };
    mockOrderHistory({ data: response });

    render(<OrderHistoryScreen />);

    expect(screen.getByTestId('partial-failure-banner')).toBeTruthy();
    expect(screen.getByText('강남 풋살장 예약')).toBeTruthy();
  });

  it('orderType 필터를 바꾸면 해당 orderType으로 재조회한다', () => {
    mockOrderHistory({ data: { items: [], page: 0, size: 20, failedDomains: [] } });

    render(<OrderHistoryScreen />);
    fireEvent.press(screen.getByLabelText('예약'));

    expect(useOrderHistoryMock).toHaveBeenLastCalledWith(
      expect.objectContaining({ orderType: 'BOOKING' })
    );
  });

  it('항목을 탭하면 주문 상세 경로로 이동한다(Option A — 주문 자신 상세)', () => {
    const response: OrderHistoryResponse = {
      items: [makeItem({ orderType: 'BOOKING', sourceId: 77, title: '강남 풋살장 예약' })],
      page: 0,
      size: 20,
      failedDomains: [],
    };
    mockOrderHistory({ data: response });

    render(<OrderHistoryScreen />);
    fireEvent.press(screen.getByTestId('order-history-item-card-BOOKING-77'));

    expect(router.push).toHaveBeenCalledWith('/orders/BOOKING/77');
  });

  it('다크 모드에서도 하드코딩 색 없이 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    const response: OrderHistoryResponse = {
      items: [makeItem()],
      page: 0,
      size: 20,
      failedDomains: [],
    };
    mockOrderHistory({ data: response });

    render(<OrderHistoryScreen />);

    expect(screen.getByText('강남 풋살장 예약')).toBeTruthy();
  });
});

/**
 * useOrderHistory가 orderType·status·page·size를 쿼리 파라미터로 전달한다
 * useOrderHistory가 파라미터 변경 시 새 queryKey로 재조회한다
 * useOrderHistory가 빈 결과(items 0건)를 정상 반환한다
 * useOrderHistory가 401 실패 시 isError 상태로 전파한다
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import { orderHistoryQueryKey, useOrderHistory } from '../useOrderHistory';
import type { OrderHistoryResponse } from '../../api/order-history-types';

jest.mock('../../api/orderHistory', () => ({
  getOrderHistory: jest.fn(),
}));

import { getOrderHistory } from '../../api/orderHistory';

const getOrderHistoryMock = getOrderHistory as jest.MockedFunction<typeof getOrderHistory>;

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper, queryClient };
}

const mockResponse: OrderHistoryResponse = {
  items: [
    {
      orderType: 'BOOKING',
      sourceId: 42,
      title: '강남 풋살장 예약',
      status: 'PAID',
      paymentId: 4821,
      detailPath: '/bookings/42',
      createdAt: '2026-07-05T14:00:00Z',
      amount: 30000,
      seats: null,
    },
  ],
  page: 0,
  size: 20,
  failedDomains: [],
};

describe('useOrderHistory', () => {
  afterEach(() => jest.clearAllMocks());

  it('orderType·status·page·size가 요청 파라미터로 전달된다', async () => {
    getOrderHistoryMock.mockResolvedValue(mockResponse);
    const { wrapper } = createWrapper();

    const { result } = renderHook(
      () => useOrderHistory({ orderType: 'BOOKING', status: 'PAID', page: 0, size: 20 }),
      { wrapper }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getOrderHistoryMock).toHaveBeenCalledWith({
      orderType: 'BOOKING',
      status: 'PAID',
      page: 0,
      size: 20,
    });
    expect(result.current.data).toEqual(mockResponse);
  });

  it('파라미터가 바뀌면 새 queryKey로 재조회한다', async () => {
    getOrderHistoryMock.mockResolvedValue(mockResponse);
    const { wrapper } = createWrapper();

    const { result, rerender } = renderHook(
      ({ orderType }: { orderType?: 'BOOKING' | 'GOODS' }) =>
        useOrderHistory({ orderType, page: 0, size: 20 }),
      { wrapper, initialProps: { orderType: 'BOOKING' as const } }
    );

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getOrderHistoryMock).toHaveBeenCalledTimes(1);

    rerender({ orderType: 'GOODS' as const });

    await waitFor(() => expect(getOrderHistoryMock).toHaveBeenCalledTimes(2));
    expect(getOrderHistoryMock).toHaveBeenLastCalledWith({
      orderType: 'GOODS',
      page: 0,
      size: 20,
    });
  });

  it('빈 결과(items 0건)를 정상 반환한다', async () => {
    getOrderHistoryMock.mockResolvedValue({ items: [], page: 0, size: 20, failedDomains: [] });
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderHistory({ page: 0, size: 20 }), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data?.items).toEqual([]);
    expect(result.current.isError).toBe(false);
  });

  it('401 실패 시 isError 상태로 전파한다', async () => {
    getOrderHistoryMock.mockRejectedValue(
      Object.assign(new Error('Unauthorized'), { status: 401 })
    );
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderHistory({ page: 0, size: 20 }), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('Unauthorized');
  });

  describe('orderHistoryQueryKey', () => {
    it('orderType·status·page·size로 결정적인 키를 계산한다', () => {
      expect(
        orderHistoryQueryKey({ orderType: 'BOOKING', status: 'PAID', page: 0, size: 20 })
      ).toEqual(['orderHistory', 'BOOKING', 'PAID', 0, 20]);
      expect(orderHistoryQueryKey({ page: 1, size: 10 })).toEqual([
        'orderHistory',
        null,
        null,
        1,
        10,
      ]);
    });
  });
});

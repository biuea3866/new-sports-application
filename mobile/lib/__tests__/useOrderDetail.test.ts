/**
 * useOrderDetail — orderType에 맞는 주문상세 API를 호출하는 TanStack Query 훅 검증.
 * 4개 orderType(BOOKING/GOODS/TICKETING/RECRUITMENT)이 각각 올바른 API 함수로
 * 라우팅되는지, 성공·실패 상태가 정상 전파되는지 검증한다.
 */
import { createElement } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { renderHook, waitFor } from '@testing-library/react-native';

import { useOrderDetail } from '../useOrderDetail';
import type {
  BookingResponse,
  GoodsOrderDetailResponse,
  TicketOrderResponse,
} from '../../api/types';
import type { ApplicationDetailResponse } from '../../api/recruitment';

jest.mock('../../api/booking', () => ({
  getBookingDetail: jest.fn(),
}));
jest.mock('../../api/goodsOrders', () => ({
  getGoodsOrderDetail: jest.fn(),
}));
jest.mock('../../api/ticketOrders', () => ({
  getTicketOrderDetail: jest.fn(),
}));
jest.mock('../../api/recruitment', () => ({
  getApplicationDetail: jest.fn(),
}));

import { getBookingDetail } from '../../api/booking';
import { getGoodsOrderDetail } from '../../api/goodsOrders';
import { getTicketOrderDetail } from '../../api/ticketOrders';
import { getApplicationDetail } from '../../api/recruitment';

const getBookingDetailMock = getBookingDetail as jest.MockedFunction<typeof getBookingDetail>;
const getGoodsOrderDetailMock = getGoodsOrderDetail as jest.MockedFunction<
  typeof getGoodsOrderDetail
>;
const getTicketOrderDetailMock = getTicketOrderDetail as jest.MockedFunction<
  typeof getTicketOrderDetail
>;
const getApplicationDetailMock = getApplicationDetail as jest.MockedFunction<
  typeof getApplicationDetail
>;

function createWrapper() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
  return { wrapper };
}

describe('useOrderDetail', () => {
  afterEach(() => jest.clearAllMocks());

  it('BOOKING이면 getBookingDetail을 호출하고 결과를 반환한다', async () => {
    const booking: BookingResponse = {
      id: 42,
      slotId: 7,
      userId: 1,
      status: 'CONFIRMED',
      paymentId: 900,
      paymentStatus: 'PAID',
      createdAt: '2026-07-05T10:00:00.000Z',
      updatedAt: '2026-07-05T10:00:00.000Z',
    };
    getBookingDetailMock.mockResolvedValue(booking);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderDetail('BOOKING', 42), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getBookingDetailMock).toHaveBeenCalledWith(42);
    expect(getGoodsOrderDetailMock).not.toHaveBeenCalled();
    expect(result.current.data).toEqual({ orderType: 'BOOKING', data: booking });
  });

  it('GOODS면 getGoodsOrderDetail을 호출하고 결과를 반환한다', async () => {
    const goodsOrder: GoodsOrderDetailResponse = {
      id: 5,
      userId: 1,
      status: 'PAID',
      totalAmount: '10000',
      paymentId: 300,
      paymentStatus: 'PAID',
      items: [],
    };
    getGoodsOrderDetailMock.mockResolvedValue(goodsOrder);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderDetail('GOODS', 5), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getGoodsOrderDetailMock).toHaveBeenCalledWith(5);
    expect(result.current.data).toEqual({ orderType: 'GOODS', data: goodsOrder });
  });

  it('TICKETING이면 getTicketOrderDetail을 호출하고 결과를 반환한다', async () => {
    const ticketOrder: TicketOrderResponse = { ticketOrderId: 12, status: 'CONFIRMED' };
    getTicketOrderDetailMock.mockResolvedValue(ticketOrder);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderDetail('TICKETING', 12), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getTicketOrderDetailMock).toHaveBeenCalledWith(12);
    expect(result.current.data).toEqual({ orderType: 'TICKETING', data: ticketOrder });
  });

  it('RECRUITMENT면 getApplicationDetail을 호출하고 결과를 반환한다', async () => {
    const application: ApplicationDetailResponse = {
      applicationId: 100,
      recruitmentId: 9,
      title: '주말 축구 3명 모집',
      status: 'CONFIRMED',
      feeAmount: 5000,
      paymentId: 200,
      createdAt: '2026-07-08T00:00:00+09:00',
    };
    getApplicationDetailMock.mockResolvedValue(application);
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderDetail('RECRUITMENT', 100), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(getApplicationDetailMock).toHaveBeenCalledWith(100);
    expect(result.current.data).toEqual({ orderType: 'RECRUITMENT', data: application });
  });

  it('API 실패 시 isError 상태로 전파한다', async () => {
    getBookingDetailMock.mockRejectedValue(new Error('Not Found'));
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderDetail('BOOKING', 999), { wrapper });

    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(result.current.error?.message).toBe('Not Found');
  });

  it('id가 0 이하이면 쿼리를 비활성화한다', () => {
    const { wrapper } = createWrapper();

    const { result } = renderHook(() => useOrderDetail('BOOKING', 0), { wrapper });

    expect(result.current.fetchStatus).toBe('idle');
    expect(getBookingDetailMock).not.toHaveBeenCalled();
  });
});

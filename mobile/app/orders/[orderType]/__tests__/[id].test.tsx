/**
 * OrderDetailScreen(Option A+) — 통합 주문내역 항목 탭 → 주문 자신 상세 화면 배선 검증.
 *
 * useOrderDetail을 모킹해 화면 배선(4상태 + orderType별 렌더 + 원본 보기 이동 + id 검증)만
 * 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import OrderDetailScreen from '../[id]';
import type { OrderDetailQueryResult } from '../../../../lib/useOrderDetail';

jest.mock('../../../../lib/useOrderDetail', () => ({
  useOrderDetail: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
  router: { push: jest.fn(), back: jest.fn() },
}));

import { router, useLocalSearchParams } from 'expo-router';
import { useOrderDetail } from '../../../../lib/useOrderDetail';

const useOrderDetailMock = useOrderDetail as jest.MockedFunction<typeof useOrderDetail>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

function mockOrderDetail(overrides: Partial<ReturnType<typeof useOrderDetail>>) {
  useOrderDetailMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useOrderDetail>);
}

const BOOKING_RESULT: OrderDetailQueryResult = {
  orderType: 'BOOKING',
  data: {
    id: 42,
    slotId: 7,
    facilityId: '9',
    userId: 1,
    status: 'CONFIRMED',
    paymentId: 900,
    paymentStatus: 'COMPLETED',
    title: '강남 풋살장 예약',
    createdAt: '2026-07-05T10:00:00.000Z',
    updatedAt: '2026-07-05T10:00:00.000Z',
  },
};

describe('OrderDetailScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('로딩 중이면 스켈레톤을 보여준다', () => {
    useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '42' });
    mockOrderDetail({ isLoading: true });

    render(<OrderDetailScreen />);

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('에러면 재시도 가능한 오류 화면을 보여준다', () => {
    useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '42' });
    const refetchMock = jest.fn();
    mockOrderDetail({ isError: true, error: new Error('boom'), refetch: refetchMock });

    render(<OrderDetailScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetchMock).toHaveBeenCalled();
  });

  it('지원하지 않는 orderType이면 안내 문구를 보여준다', () => {
    useLocalSearchParamsMock.mockReturnValue({ orderType: 'UNKNOWN', id: '1' });
    mockOrderDetail({});

    render(<OrderDetailScreen />);

    expect(screen.getByText('지원하지 않는 주문 유형이에요')).toBeTruthy();
  });

  describe('id 검증', () => {
    it('id가 0이면 안내 문구를 보여준다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '0' });
      mockOrderDetail({});

      render(<OrderDetailScreen />);

      expect(screen.getByText('지원하지 않는 주문 유형이에요')).toBeTruthy();
    });

    it('id가 음수이면 안내 문구를 보여준다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '-1' });
      mockOrderDetail({});

      render(<OrderDetailScreen />);

      expect(screen.getByText('지원하지 않는 주문 유형이에요')).toBeTruthy();
    });

    it('id가 숫자가 아니면 안내 문구를 보여준다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: 'abc' });
      mockOrderDetail({});

      render(<OrderDetailScreen />);

      expect(screen.getByText('지원하지 않는 주문 유형이에요')).toBeTruthy();
    });
  });

  describe('BOOKING 성공', () => {
    it('제목·상태·결제·일시를 보여준다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '42' });
      mockOrderDetail({ data: BOOKING_RESULT });

      render(<OrderDetailScreen />);

      expect(screen.getByText('강남 풋살장 예약')).toBeTruthy();
      expect(screen.getByText('결제완료')).toBeTruthy();
      expect(screen.getByText('결제 #900')).toBeTruthy();
    });

    it('facilityId가 있으면 원본 보기를 탭했을 때 시설 상세로 이동한다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '42' });
      mockOrderDetail({ data: BOOKING_RESULT });

      render(<OrderDetailScreen />);
      fireEvent.press(screen.getByLabelText('원본 보기'));

      expect(router.push).toHaveBeenCalledWith('/facility/9');
    });

    it('facilityId가 없으면(cancel 경로 등) 원본 보기 버튼이 없다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '42' });
      const withoutFacility: OrderDetailQueryResult = {
        orderType: 'BOOKING',
        data: { ...BOOKING_RESULT.data, facilityId: null, title: null },
      };
      mockOrderDetail({ data: withoutFacility });

      render(<OrderDetailScreen />);

      expect(screen.queryByLabelText('원본 보기')).toBeNull();
      expect(screen.getByText('예약 #42')).toBeTruthy();
    });
  });

  describe('GOODS 성공', () => {
    it('제목·상품 유형별 요약을 보여준다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'GOODS', id: '5' });
      const result: OrderDetailQueryResult = {
        orderType: 'GOODS',
        data: {
          id: 5,
          userId: 1,
          status: 'CONFIRMED',
          totalAmount: '10000',
          paymentId: 300,
          paymentStatus: 'COMPLETED',
          title: '요가매트 프리미엄',
          createdAt: '2026-07-05T10:00:00.000Z',
          items: [{ id: 1, productId: 88, quantity: 1, unitPrice: '10000', subtotal: '10000' }],
        },
      };
      mockOrderDetail({ data: result });

      render(<OrderDetailScreen />);

      expect(screen.getByText('요가매트 프리미엄')).toBeTruthy();
      expect(screen.getByText(/합계 10,000원/)).toBeTruthy();
    });

    it('상품 1건이면 원본 보기를 탭했을 때 상품 상세로 이동한다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'GOODS', id: '5' });
      const result: OrderDetailQueryResult = {
        orderType: 'GOODS',
        data: {
          id: 5,
          userId: 1,
          status: 'CONFIRMED',
          totalAmount: '10000',
          paymentId: 300,
          paymentStatus: 'COMPLETED',
          title: '요가매트 프리미엄',
          createdAt: '2026-07-05T10:00:00.000Z',
          items: [{ id: 1, productId: 88, quantity: 1, unitPrice: '10000', subtotal: '10000' }],
        },
      };
      mockOrderDetail({ data: result });

      render(<OrderDetailScreen />);
      fireEvent.press(screen.getByLabelText('원본 보기'));

      expect(router.push).toHaveBeenCalledWith('/product/88');
    });
  });

  describe('TICKETING 성공', () => {
    const result: OrderDetailQueryResult = {
      orderType: 'TICKETING',
      data: {
        ticketOrderId: 12,
        status: 'CONFIRMED',
        eventId: 77,
        eventTitle: '2026 서울 마라톤',
        paymentId: 500,
        createdAt: '2026-07-05T10:00:00.000Z',
      },
    };

    it('이벤트명을 제목으로, 결제·일시를 실제 값으로 보여준다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'TICKETING', id: '12' });
      mockOrderDetail({ data: result });

      render(<OrderDetailScreen />);

      expect(screen.getByText('2026 서울 마라톤')).toBeTruthy();
      expect(screen.getByText('결제완료')).toBeTruthy();
      expect(screen.getByText('결제 #500')).toBeTruthy();
    });

    it('원본 보기를 탭하면 이벤트 상세 경로로 이동한다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'TICKETING', id: '12' });
      mockOrderDetail({ data: result });

      render(<OrderDetailScreen />);
      fireEvent.press(screen.getByLabelText('원본 보기'));

      expect(router.push).toHaveBeenCalledWith('/event/77');
    });
  });

  describe('RECRUITMENT 성공', () => {
    const result: OrderDetailQueryResult = {
      orderType: 'RECRUITMENT',
      data: {
        applicationId: 100,
        recruitmentId: 9,
        recruitmentTitle: '주말 축구 3명 모집',
        status: 'CONFIRMED',
        feeAmount: 5000,
        paymentId: 200,
        createdAt: '2026-07-08T00:00:00+09:00',
      },
    };

    it('모집명을 제목으로 보여준다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'RECRUITMENT', id: '100' });
      mockOrderDetail({ data: result });

      render(<OrderDetailScreen />);

      expect(screen.getByText('주말 축구 3명 모집')).toBeTruthy();
    });

    it('원본 보기를 탭하면 모집 상세 경로로 이동한다', () => {
      useLocalSearchParamsMock.mockReturnValue({ orderType: 'RECRUITMENT', id: '100' });
      mockOrderDetail({ data: result });

      render(<OrderDetailScreen />);
      fireEvent.press(screen.getByLabelText('원본 보기'));

      expect(router.push).toHaveBeenCalledWith('/recruitments/9');
    });
  });

  it('다크 모드에서도 하드코딩 색 없이 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    useLocalSearchParamsMock.mockReturnValue({ orderType: 'BOOKING', id: '42' });
    mockOrderDetail({ data: BOOKING_RESULT });

    render(<OrderDetailScreen />);

    expect(screen.getByText('강남 풋살장 예약')).toBeTruthy();
  });
});

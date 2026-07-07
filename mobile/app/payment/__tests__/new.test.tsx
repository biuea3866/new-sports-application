/**
 * PaymentNewScreen(MO-08 확장) — RECRUITMENT + pre-issued 결제 진입 모드 검증.
 * 근거: design-fe-app.md "결제 흐름 재사용 결정"("checkoutUrl·paymentId가 라우트
 * 파라미터로 오면 preparePayment를 건너뛰고 바로 Linking.openURL → getPayment 폴링").
 *
 * 기존 BOOKING/GOODS/TICKETING 경로는 무변경(additive)임을 회귀 테스트로 확인한다.
 */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import PaymentNewScreen from '../new';

jest.mock('../../../api/payment', () => {
  const actual = jest.requireActual('../../../api/payment');
  return {
    ...actual,
    preparePayment: jest.fn(),
    getPayment: jest.fn(),
  };
});

jest.mock('expo-linking', () => ({
  openURL: jest.fn(() => Promise.resolve(true)),
  createURL: jest.fn(() => 'exp://test/payment/result'),
}));

import * as Linking from 'expo-linking';
import { getPayment, preparePayment } from '../../../api/payment';
import { useLocalSearchParams } from 'expo-router';

const preparePaymentMock = preparePayment as jest.MockedFunction<typeof preparePayment>;
const getPaymentMock = getPayment as jest.MockedFunction<typeof getPayment>;
const openURLMock = Linking.openURL as jest.MockedFunction<typeof Linking.openURL>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

describe('PaymentNewScreen — 기존 BOOKING 경로(회귀)', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('결제수단 선택 화면이 그대로 렌더된다(pre-issued 아님)', () => {
    useLocalSearchParamsMock.mockReturnValue({
      orderType: 'BOOKING',
      orderId: '1',
      amount: '10000',
    });

    render(<PaymentNewScreen />);

    expect(screen.getByText('결제 수단 선택')).toBeTruthy();
    expect(screen.getByLabelText('카카오페이')).toBeTruthy();
  });

  it('결제수단 선택 후 결제하기를 탭하면 preparePayment가 호출된다', async () => {
    useLocalSearchParamsMock.mockReturnValue({
      orderType: 'BOOKING',
      orderId: '1',
      amount: '10000',
    });
    preparePaymentMock.mockResolvedValue({
      paymentId: 1,
      checkoutUrl: 'https://mock-pg.example.com/checkout/xyz',
      pgTransactionId: 'tx-1',
    });
    getPaymentMock.mockResolvedValue({
      id: 1,
      orderType: 'BOOKING',
      orderId: 1,
      method: 'KAKAO',
      amount: 10000,
      currency: 'KRW',
      status: 'COMPLETED',
      itemName: '주문 상품',
      pgTransactionId: 'tx-1',
      createdAt: '2026-07-08T00:00:00+09:00',
      paidAt: '2026-07-08T00:00:01+09:00',
    });

    render(<PaymentNewScreen />);
    fireEvent.press(screen.getByLabelText('카카오페이'));
    fireEvent.press(screen.getByLabelText('결제하기'));

    await waitFor(() => expect(preparePaymentMock).toHaveBeenCalled());
    expect(preparePaymentMock).toHaveBeenCalledWith(
      expect.objectContaining({ orderType: 'BOOKING', orderId: 1 }),
      expect.any(String)
    );
  });
});

describe('PaymentNewScreen — RECRUITMENT pre-issued 진입', () => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('checkoutUrl·paymentId 파라미터가 있으면 preparePayment 없이 바로 결제창을 열고 폴링한다', async () => {
    useLocalSearchParamsMock.mockReturnValue({
      orderType: 'RECRUITMENT',
      orderId: '100',
      paymentId: '200',
      checkoutUrl: 'https://mock-pg.example.com/checkout/recruitment-abc',
    });
    getPaymentMock.mockResolvedValue({
      id: 200,
      orderType: 'RECRUITMENT',
      orderId: 100,
      method: 'CREDIT_CARD',
      amount: 5000,
      currency: 'KRW',
      status: 'COMPLETED',
      itemName: '모집 신청',
      pgTransactionId: 'tx-2',
      createdAt: '2026-07-08T00:00:00+09:00',
      paidAt: '2026-07-08T00:00:01+09:00',
    });

    render(<PaymentNewScreen />);

    await waitFor(() =>
      expect(openURLMock).toHaveBeenCalledWith(
        'https://mock-pg.example.com/checkout/recruitment-abc'
      )
    );
    expect(preparePaymentMock).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByText('결제 완료')).toBeTruthy());
  });

  it('폴링 결과가 FAILED면 실패 화면과 다시 시도 버튼이 표시된다', async () => {
    useLocalSearchParamsMock.mockReturnValue({
      orderType: 'RECRUITMENT',
      orderId: '100',
      paymentId: '200',
      checkoutUrl: 'https://mock-pg.example.com/checkout/recruitment-abc',
    });
    getPaymentMock.mockResolvedValue({
      id: 200,
      orderType: 'RECRUITMENT',
      orderId: 100,
      method: 'CREDIT_CARD',
      amount: 5000,
      currency: 'KRW',
      status: 'FAILED',
      itemName: '모집 신청',
      pgTransactionId: null,
      createdAt: '2026-07-08T00:00:00+09:00',
      paidAt: null,
    });

    render(<PaymentNewScreen />);

    await waitFor(() => expect(screen.getByText('결제 실패')).toBeTruthy());
  });
});

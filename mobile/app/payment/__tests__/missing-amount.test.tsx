/**
 * 결제 화면 — 결제 금액이 전달되지 않은 진입에 대한 방어.
 *
 * 회귀 배경: 주문 확인 화면 합계가 88,000원인데 결제 수단 선택 화면이 큰 파란 글씨로 "0원"을
 * 표시했다(유즈케이스 캡쳐 19-결제-수단-선택). amount 쿼리 파라미터가 없거나 유효하지 않을 때
 * 0을 실제 결제 금액처럼 렌더한 것이 원인이다. 결제 금액을 모르는 상태에서는 금액을 지어내지
 * 않고 오류 상태를 보여줘야 한다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { useLocalSearchParams } from 'expo-router';

import PaymentScreen from '../index';
import PaymentNewScreen from '../new';

jest.mock('expo-linking', () => ({
  openURL: jest.fn(() => Promise.resolve(true)),
  createURL: jest.fn(() => 'exp://test/payment/result'),
}));

const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

describe.each([
  ['PaymentScreen(/payment)', () => <PaymentScreen />],
  ['PaymentNewScreen(/payment/new)', () => <PaymentNewScreen />],
])('%s — 결제 금액 누락 방어', (_name, renderScreen) => {
  afterEach(() => {
    jest.clearAllMocks();
  });

  it('amount 파라미터 없이 진입하면 0원이 아니라 결제 정보 오류를 안내한다', () => {
    useLocalSearchParamsMock.mockReturnValue({});

    render(renderScreen());

    expect(screen.queryByText('0원')).toBeNull();
    expect(screen.getByText('결제 정보를 불러올 수 없습니다.')).toBeTruthy();
  });

  it('amount가 0이면 결제 수단 선택 대신 오류를 안내한다', () => {
    useLocalSearchParamsMock.mockReturnValue({
      orderType: 'TICKETING',
      orderId: '9',
      amount: '0',
    });

    render(renderScreen());

    expect(screen.queryByText('0원')).toBeNull();
    expect(screen.queryByLabelText('카카오페이')).toBeNull();
    expect(screen.getByText('결제 정보를 불러올 수 없습니다.')).toBeTruthy();
  });

  it('amount가 숫자가 아니면 오류를 안내한다', () => {
    useLocalSearchParamsMock.mockReturnValue({
      orderType: 'TICKETING',
      orderId: '9',
      amount: 'abc',
    });

    render(renderScreen());

    expect(screen.getByText('결제 정보를 불러올 수 없습니다.')).toBeTruthy();
  });

  it('유효한 amount로 진입하면 금액과 결제 수단이 정상 표시된다', () => {
    useLocalSearchParamsMock.mockReturnValue({
      orderType: 'TICKETING',
      orderId: '9',
      amount: '88000',
    });

    render(renderScreen());

    expect(screen.getByText('88,000원')).toBeTruthy();
    expect(screen.getByLabelText('카카오페이')).toBeTruthy();
    expect(screen.queryByText('결제 정보를 불러올 수 없습니다.')).toBeNull();
  });
});

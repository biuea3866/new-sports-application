/**
 * LimitedDropDetailScreen(S1) — 상품명 노출 검증.
 *
 * 회귀 배경: 한정판 상세 화면에 상품명이 어디에도 없어, 사용자가 무엇을 사는지 알 수 없었다
 * (유즈케이스 캡쳐 14-한정판-드롭). 회차 응답에 productName 이 없던 것이 원인이다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { LimitedDropResponse } from '../../../../api/types';
import LimitedDropDetailScreen from '../index';

jest.mock('../../../../lib/useLimitedDrop', () => ({
  useLimitedDrop: jest.fn(),
}));
jest.mock('../../../../lib/useCountdown', () => ({
  useCountdown: jest.fn(),
}));
jest.mock('../../../../lib/feature-flags', () => ({
  isFeatureEnabled: jest.fn(() => false),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useCountdown } from '../../../../lib/useCountdown';
import { useLimitedDrop } from '../../../../lib/useLimitedDrop';

const useLimitedDropMock = useLimitedDrop as jest.MockedFunction<typeof useLimitedDrop>;
const useCountdownMock = useCountdown as jest.MockedFunction<typeof useCountdown>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const openDrop: LimitedDropResponse = {
  dropId: 1,
  productId: 100,
  productName: '실내 클라이밍 초크백',
  productImageUrl: 'https://cdn.example.com/100.jpg',
  status: 'OPEN',
  openAt: '2026-07-05T20:00:00Z',
  closeAt: '2026-07-06T20:00:00Z',
  remaining: 300,
  perUserLimit: 2,
  totalQuantity: 300,
  price: 119000,
};

describe('LimitedDropDetailScreen — 상품명', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    useRouterMock.mockReturnValue({
      push: jest.fn(),
      back: jest.fn(),
      replace: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    useCountdownMock.mockReturnValue({ remainingMs: 0, isOpen: true });
    useLimitedDropMock.mockReturnValue({
      data: openDrop,
      isLoading: false,
      isError: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useLimitedDrop>);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('상품명을 화면 제목으로 렌더한다', () => {
    render(<LimitedDropDetailScreen />);

    expect(screen.getByText('실내 클라이밍 초크백')).toBeTruthy();
  });

  it('상품명과 함께 가격·남은 수량을 보여준다', () => {
    render(<LimitedDropDetailScreen />);

    expect(screen.getByText('119,000원')).toBeTruthy();
    expect(screen.getByText('남은 수량 300/300')).toBeTruthy();
  });

  it('상품명이 비어 있어도 화면이 깨지지 않는다', () => {
    useLimitedDropMock.mockReturnValue({
      data: { ...openDrop, productName: '' },
      isLoading: false,
      isError: false,
      error: null,
      refetch: jest.fn(),
    } as unknown as ReturnType<typeof useLimitedDrop>);

    render(<LimitedDropDetailScreen />);

    expect(screen.getByText('한정판 상품')).toBeTruthy();
  });
});

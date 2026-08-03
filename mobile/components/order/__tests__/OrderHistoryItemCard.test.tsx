/**
 * OrderHistoryItemCard — orderType 배지·status 한글 라벨·title 주 표시명(+fallback)·
 * 결제 연계 문구를 렌더하고, 탭 시 onPress가 detailPath와 함께 호출되는지 검증한다.
 * 색은 useTheme() 토큰을 경유하는지 라이트/다크 두 모드에서 검증한다.
 */
import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import { OrderHistoryItemCard } from '../OrderHistoryItemCard';
import type { OrderHistoryItem } from '../../../api/order-history-types';

jest.mock('../../../theme/useTheme', () => ({
  useTheme: jest.fn(),
}));

import { useTheme } from '../../../theme/useTheme';
import { lightTokens, darkTokens } from '../../../theme/tokens';

const useThemeMock = useTheme as jest.MockedFunction<typeof useTheme>;

function makeOrderHistoryItem(overrides: Partial<OrderHistoryItem> = {}): OrderHistoryItem {
  return {
    orderType: 'BOOKING',
    sourceId: 4821,
    title: '강남 풋살장 예약',
    status: 'CONFIRMED',
    paymentId: 4821,
    detailPath: '/booking/4821',
    createdAt: '2026-07-05T10:00:00.000Z',
    amount: 50000,
    seats: null,
    ...overrides,
  };
}

function testIdFor(item: OrderHistoryItem): string {
  return `order-history-item-card-${item.orderType}-${item.sourceId}`;
}

describe('OrderHistoryItemCard', () => {
  beforeEach(() => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
  });

  it('orderType 배지와 status 한글 라벨을 렌더한다', () => {
    const item = makeOrderHistoryItem({ orderType: 'BOOKING', status: 'PENDING' });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('예약')).toBeTruthy();
    expect(screen.getByText('대기')).toBeTruthy();
  });

  it('title을 주 표시명으로 렌더한다', () => {
    const item = makeOrderHistoryItem({ title: '강남 풋살장 예약' });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('강남 풋살장 예약')).toBeTruthy();
  });

  it('title이 비어 있으면 유형명 #sourceId fallback을 렌더한다', () => {
    const item = makeOrderHistoryItem({ title: '', orderType: 'GOODS', sourceId: 1203 });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('상품 #1203')).toBeTruthy();
  });

  it('paymentId가 있어도 내부 결제 PK를 렌더하지 않는다', () => {
    const item = makeOrderHistoryItem({ paymentId: 4790 });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.queryByText(/결제 #/)).toBeNull();
    expect(screen.queryByText('4790')).toBeNull();
  });

  it('paymentId가 없으면 미결제를 렌더한다', () => {
    const item = makeOrderHistoryItem({ paymentId: null });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('미결제')).toBeTruthy();
  });

  it('탭하면 onPress가 항목 detailPath와 함께 호출된다', () => {
    const item = makeOrderHistoryItem({ detailPath: '/booking/4821' });
    const onPress = jest.fn();

    render(<OrderHistoryItemCard item={item} onPress={onPress} />);
    fireEvent.press(screen.getByTestId(testIdFor(item)));

    expect(onPress).toHaveBeenCalledTimes(1);
    expect(onPress).toHaveBeenCalledWith('/booking/4821');
  });

  it('결제완료(CONFIRMED) 상태는 success 점 강조로 렌더된다', () => {
    const item = makeOrderHistoryItem({ status: 'CONFIRMED' });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('결제완료')).toBeTruthy();
    expect(screen.getByTestId(`${testIdFor(item)}-status-dot`)).toHaveStyle({
      backgroundColor: lightTokens.success,
    });
  });

  it('결제완료가 아닌 상태는 success 점을 렌더하지 않는다', () => {
    const item = makeOrderHistoryItem({ status: 'PENDING' });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.queryByTestId(`${testIdFor(item)}-status-dot`)).toBeNull();
  });

  it('금액이 있으면 천단위 구분자와 원 단위로 렌더한다', () => {
    const item = makeOrderHistoryItem({ amount: 50000 });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('50,000원')).toBeTruthy();
  });

  it('금액이 0(무료 확정값)이면 무료로 렌더한다', () => {
    const item = makeOrderHistoryItem({ amount: 0 });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('무료')).toBeTruthy();
  });

  it('금액이 null이면(금액 확정 불가) 금액 줄을 렌더하지 않는다', () => {
    const item = makeOrderHistoryItem({ amount: null });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.queryByTestId(`${testIdFor(item)}-amount`)).toBeNull();
  });

  it('좌석 정보가 있으면 서술형 라벨로 부가 정보를 렌더한다(같은 제목 주문 구분용)', () => {
    const item = makeOrderHistoryItem({
      orderType: 'TICKETING',
      seats: [
        { section: 'R', rowNo: '1', seatNo: 'R-01' },
        { section: 'R', rowNo: '1', seatNo: 'R-02' },
      ],
    });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByText('R구역 1열 01번 외 1석')).toBeTruthy();
  });

  it('좌석 정보가 없으면 부가 정보를 렌더하지 않는다', () => {
    const item = makeOrderHistoryItem({ seats: null });

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.queryByTestId(`${testIdFor(item)}-seats`)).toBeNull();
  });

  it('라이트 모드에서 surface 토큰으로 카드가 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
    const item = makeOrderHistoryItem();

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByTestId(testIdFor(item))).toHaveStyle({
      backgroundColor: lightTokens.surface,
    });
  });

  it('다크 모드에서 surface 토큰으로 카드가 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });
    const item = makeOrderHistoryItem();

    render(<OrderHistoryItemCard item={item} onPress={jest.fn()} />);

    expect(screen.getByTestId(testIdFor(item))).toHaveStyle({
      backgroundColor: darkTokens.surface,
    });
  });
});

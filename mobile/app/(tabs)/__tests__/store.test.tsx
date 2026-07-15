/**
 * StoreTabScreen — 굿즈|티켓 세그먼트 통합 화면 사용자 관점 동작 검증.
 * 근거: 사용자 피드백 "스토어 = 기존 스토어(굿즈) + 티켓을 세그먼트 컨트롤로 통합".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { ListEventsResponse, EventResponse } from '../../../api/types';
import type { ProductWithStock } from '../../../api/goods';
import StoreTabScreen from '../store';

jest.mock('../../../api/goods', () => ({
  useProducts: jest.fn(),
}));

jest.mock('../../../lib/useEvents', () => ({
  useEvents: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
  useLocalSearchParams: jest.fn(),
}));

import { useRouter, useLocalSearchParams } from 'expo-router';
import { useProducts } from '../../../api/goods';
import { useEvents } from '../../../lib/useEvents';

const useProductsMock = useProducts as jest.MockedFunction<typeof useProducts>;
const useEventsMock = useEvents as jest.MockedFunction<typeof useEvents>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

const PRODUCT: ProductWithStock = {
  id: 1,
  name: '유니폼',
  category: 'APPAREL',
  price: 89000,
  description: '',
  imageUrl: '',
  status: 'ACTIVE',
  stockQuantity: 3,
};

function eventsPage(content: EventResponse[]): ListEventsResponse {
  return { content, totalElements: content.length, totalPages: 1, number: 0, size: 20 };
}

const EVENT: EventResponse = {
  id: 10,
  title: '결승전',
  venue: '잠실 종합운동장',
  startsAt: '2026-08-01T18:00:00Z',
  status: 'OPEN',
} as EventResponse;

function mockProducts(overrides: Partial<ReturnType<typeof useProducts>> = {}) {
  useProductsMock.mockReturnValue({
    data: [PRODUCT],
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useProducts>);
}

function mockEvents(overrides: Partial<ReturnType<typeof useEvents>> = {}) {
  useEventsMock.mockReturnValue({
    data: eventsPage([EVENT]),
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useEvents>);
}

describe('스토어 탭 화면 — 굿즈|티켓 세그먼트', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useRouterMock.mockReturnValue({ push: pushMock } as unknown as ReturnType<typeof useRouter>);
    useLocalSearchParamsMock.mockReturnValue({});
    mockProducts();
    mockEvents();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('기본 진입 시 굿즈 세그먼트가 선택되어 상품 목록을 보여준다', () => {
    render(<StoreTabScreen />);

    expect(screen.getByText('유니폼')).toBeTruthy();
    expect(screen.getByRole('button', { name: '굿즈' }).props.accessibilityState.selected).toBe(
      true
    );
  });

  it('티켓 세그먼트를 탭하면 경기 목록으로 전환된다', () => {
    render(<StoreTabScreen />);
    fireEvent.press(screen.getByRole('button', { name: '티켓' }));

    expect(screen.getByText('결승전')).toBeTruthy();
    expect(screen.queryByText('유니폼')).toBeNull();
  });

  it('segment=tickets 쿼리 파라미터로 진입하면 티켓 세그먼트가 처음부터 선택된다', () => {
    useLocalSearchParamsMock.mockReturnValue({ segment: 'tickets' });

    render(<StoreTabScreen />);

    expect(screen.getByText('결승전')).toBeTruthy();
    expect(screen.getByRole('button', { name: '티켓' }).props.accessibilityState.selected).toBe(
      true
    );
  });

  it('상품 카드를 탭하면 상품 상세로 이동한다', () => {
    render(<StoreTabScreen />);
    fireEvent.press(screen.getByLabelText(/유니폼/));

    expect(pushMock).toHaveBeenCalledWith('/product/1');
  });

  it('티켓 세그먼트에서 경기 카드를 탭하면 경기 상세로 이동한다', () => {
    render(<StoreTabScreen />);
    fireEvent.press(screen.getByRole('button', { name: '티켓' }));
    fireEvent.press(screen.getByLabelText('결승전 경기 상세 보기'));

    expect(pushMock).toHaveBeenCalledWith('/event/10');
  });

  it('굿즈 목록이 비어있으면 빈 상태를 표시한다', () => {
    mockProducts({ data: [] });

    render(<StoreTabScreen />);

    expect(screen.getByText('등록된 상품이 없습니다.')).toBeTruthy();
  });

  it('굿즈 조회 실패 시 에러 뷰와 재시도 버튼을 표시한다', () => {
    const refetch = jest.fn();
    mockProducts({ isError: true, refetch });

    render(<StoreTabScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('티켓 조회 실패 시 에러 뷰와 재시도 버튼을 표시한다', () => {
    const refetch = jest.fn();
    mockEvents({ isError: true, refetch });

    render(<StoreTabScreen />);
    fireEvent.press(screen.getByRole('button', { name: '티켓' }));
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<StoreTabScreen />);

    expect(screen.getByText('유니폼')).toBeTruthy();
  });
});

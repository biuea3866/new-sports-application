/**
 * LimitedDropDetailScreen(S1) — 회차 상태별 hero·CTA와 loading/error 상태를 사용자 관점으로 검증.
 * 근거: FE-06 티켓 "테스트 케이스" · design-fe-app.md "화면별 상태 표"
 *
 * useLimitedDrop·useCountdown(기반 훅)만 모킹해 useLimitedDropDetail의 실제 합성 로직이
 * 그대로 동작한 상태로 화면을 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { LimitedDropResponse } from '../../../../api/types';
import LimitedDropDetailScreen from '../index';

jest.mock('../../../../lib/useLimitedDrop', () => ({
  useLimitedDrop: jest.fn(),
}));
jest.mock('../../../../lib/useCountdown', () => ({
  useCountdown: jest.fn(),
}));

import { useLocalSearchParams } from 'expo-router';
import { useCountdown } from '../../../../lib/useCountdown';
import { useLimitedDrop } from '../../../../lib/useLimitedDrop';

const useLimitedDropMock = useLimitedDrop as jest.MockedFunction<typeof useLimitedDrop>;
const useCountdownMock = useCountdown as jest.MockedFunction<typeof useCountdown>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

const baseDrop: LimitedDropResponse = {
  dropId: 1,
  productId: 100,
  status: 'SCHEDULED',
  openAt: '2026-07-05T20:00:00Z',
  closeAt: '2026-07-06T20:00:00Z',
  remaining: 32,
  perUserLimit: 2,
};

function mockUseLimitedDropReturn(overrides: Partial<ReturnType<typeof useLimitedDrop>>) {
  useLimitedDropMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as ReturnType<typeof useLimitedDrop>);
}

describe('LimitedDropDetailScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    useCountdownMock.mockReturnValue({ remainingMs: 5000, isOpen: false });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('조회 중이면 loading 스피너를 표시한다', () => {
    mockUseLimitedDropReturn({ isLoading: true });

    render(<LimitedDropDetailScreen />);

    expect(screen.getByLabelText('회차 정보 불러오는 중')).toBeTruthy();
  });

  it('404 응답 시 "없는 회차" 에러 UI를 표시한다', () => {
    const notFoundError = new AxiosError('Not Found', undefined, undefined, undefined, {
      status: 404,
      data: {},
      statusText: 'Not Found',
      headers: {},
      config: {} as never,
    });
    mockUseLimitedDropReturn({ isError: true, error: notFoundError });

    render(<LimitedDropDetailScreen />);

    expect(screen.getByText('존재하지 않는 회차예요')).toBeTruthy();
    expect(screen.getByLabelText('다시 시도')).toBeTruthy();
  });

  it('SCHEDULED일 때 카운트다운과 비활성 "판매 시작 전" CTA를 표시한다', () => {
    mockUseLimitedDropReturn({ data: { ...baseDrop, status: 'SCHEDULED' } });
    useCountdownMock.mockReturnValue({ remainingMs: 8093000, isOpen: false });

    render(<LimitedDropDetailScreen />);

    expect(screen.getByText('02:14:53')).toBeTruthy();
    const cta = screen.getByLabelText('판매 시작 전');
    expect(cta.props.accessibilityState.disabled).toBe(true);
  });

  it('OPEN일 때 남은 수량과 활성 "구매하기" CTA를 표시한다', () => {
    mockUseLimitedDropReturn({ data: { ...baseDrop, status: 'OPEN', remaining: 32 } });
    useCountdownMock.mockReturnValue({ remainingMs: 0, isOpen: true });

    render(<LimitedDropDetailScreen />);

    expect(screen.getByText(/남은 수량 32개/)).toBeTruthy();
    const cta = screen.getByLabelText('구매하기');
    expect(cta.props.accessibilityState.disabled).toBe(false);
  });

  it('SOLD_OUT일 때 비활성 "재고 소진"을 표시한다', () => {
    mockUseLimitedDropReturn({ data: { ...baseDrop, status: 'SOLD_OUT', remaining: 0 } });

    render(<LimitedDropDetailScreen />);

    const cta = screen.getByLabelText('재고 소진');
    expect(cta.props.accessibilityState.disabled).toBe(true);
  });

  it('CLOSED일 때 비활성 "판매 종료"를 표시한다', () => {
    mockUseLimitedDropReturn({ data: { ...baseDrop, status: 'CLOSED' } });

    render(<LimitedDropDetailScreen />);

    const cta = screen.getByLabelText('판매 종료');
    expect(cta.props.accessibilityState.disabled).toBe(true);
  });

  it('openAt 도달 시 CTA가 활성화된다', () => {
    mockUseLimitedDropReturn({ data: { ...baseDrop, status: 'SCHEDULED' } });
    useCountdownMock.mockReturnValue({ remainingMs: 1000, isOpen: false });

    render(<LimitedDropDetailScreen />);
    expect(screen.getByLabelText('판매 시작 전').props.accessibilityState.disabled).toBe(true);

    useCountdownMock.mockReturnValue({ remainingMs: 0, isOpen: true });
    render(<LimitedDropDetailScreen />);

    expect(screen.getByLabelText('구매하기').props.accessibilityState.disabled).toBe(false);
  });

  it('다시 시도 버튼을 누르면 refetch를 호출한다', () => {
    const refetchMock = jest.fn();
    mockUseLimitedDropReturn({ isError: true, error: new Error('boom'), refetch: refetchMock });

    render(<LimitedDropDetailScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetchMock).toHaveBeenCalled();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockUseLimitedDropReturn({ data: { ...baseDrop, status: 'OPEN' } });
    useCountdownMock.mockReturnValue({ remainingMs: 0, isOpen: true });

    render(<LimitedDropDetailScreen />);

    expect(screen.getByLabelText('구매하기')).toBeTruthy();
  });
});

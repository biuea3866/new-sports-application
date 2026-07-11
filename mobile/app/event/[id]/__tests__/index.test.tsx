/**
 * EventDetailScreen — 예매 진입(티켓 구매 CTA)의 가상 대기열 분기 검증(FE-09).
 * 근거: FE-09 티켓 "테스트 케이스", design-fe-app.md "라우팅·내비게이션 흐름"·시나리오 6·7.
 *
 * useEvent를 모킹해 좌석 선택 → CTA 클릭 시 이동 경로가 플래그 값에 따라
 * 대기실(`ticketing-event`) 또는 기존 order 화면으로 갈리는지 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';

import type { EventDetailResponse } from '../../../../api/types';
import EventDetailScreen from '../index';

jest.mock('../../../../lib/useEvent', () => ({
  useEvent: jest.fn(),
}));

jest.mock('../../../../lib/feature-flags', () => ({
  isFeatureEnabled: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), back: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: jest.fn(),
}));

import { router, useLocalSearchParams } from 'expo-router';
import { isFeatureEnabled } from '../../../../lib/feature-flags';
import { useEvent } from '../../../../lib/useEvent';

const useEventMock = useEvent as jest.MockedFunction<typeof useEvent>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

const baseEvent: EventDetailResponse = {
  id: 1,
  title: '2026 서울 마라톤',
  venue: '잠실 종합운동장',
  startsAt: '2026-08-01T09:00:00Z',
  status: 'OPEN',
  sections: [{ section: 'A', totalSeats: 100 }],
  seats: [{ id: 10, section: 'A', rowNo: '1', seatNo: '1', price: '50000', available: true }],
};

function mockUseEventReturn(overrides: Partial<ReturnType<typeof useEvent>>) {
  useEventMock.mockReturnValue({
    data: baseEvent,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as ReturnType<typeof useEvent>);
}

function selectFirstSeat() {
  fireEvent.press(screen.getByLabelText('A구역 1열 1번'));
}

describe('EventDetailScreen', () => {
  beforeEach(() => {
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    mockUseEventReturn({});
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('플래그 ON이면 예매 진입이 대기실 경로(ticketing-event)로 이동한다', () => {
    isFeatureEnabledMock.mockReturnValue(true);

    render(<EventDetailScreen />);
    selectFirstSeat();
    fireEvent.press(screen.getByLabelText('티켓 구매 1석 선택됨'));

    expect(router.push).toHaveBeenCalledWith('/queue/ticketing-event/1');
  });

  it('플래그 OFF이면 예매 진입이 기존 order 화면으로 직접 이동한다', () => {
    isFeatureEnabledMock.mockReturnValue(false);

    render(<EventDetailScreen />);
    selectFirstSeat();
    fireEvent.press(screen.getByLabelText('티켓 구매 1석 선택됨'));

    expect(router.push).toHaveBeenCalledWith('/event/1/order?seatIds=10');
  });

  it('좌석을 선택하지 않으면 CTA가 비활성 상태다', () => {
    isFeatureEnabledMock.mockReturnValue(true);

    render(<EventDetailScreen />);

    const cta = screen.getByLabelText('티켓 구매 ');
    expect(cta.props.accessibilityState.disabled).toBe(true);
  });
});

/**
 * CommunityBookingSection(A-B1) — 소모임 예약 목록 4상태·방장 전용 CTA 게이팅 검증.
 * 근거: design-fe-app.md Testing Plan "소모임 예약".
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CommunityBookingListItemResponse } from '../../../api/community-types';
import { CommunityBookingSection } from '../CommunityBookingSection';

jest.mock('../../../lib/useCommunityBooking', () => ({
  useCommunityBookings: jest.fn(),
}));

import { useCommunityBookings } from '../../../lib/useCommunityBooking';

const useCommunityBookingsMock = useCommunityBookings as jest.MockedFunction<
  typeof useCommunityBookings
>;

function forbiddenError(): AxiosError {
  return new AxiosError('Forbidden', undefined, undefined, undefined, {
    status: 403,
    data: {},
    statusText: 'Forbidden',
    headers: {},
    config: {} as never,
  });
}

const BOOKING: CommunityBookingListItemResponse = {
  id: 1,
  communityId: 5,
  slotId: 100,
  linkedByUserId: 10,
  facilityId: '체육관 A',
  date: '2026-07-12',
  timeRange: '14:00~15:00',
  capacity: 8,
};

function mockBookings(overrides: Partial<ReturnType<typeof useCommunityBookings>>) {
  useCommunityBookingsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCommunityBookings>);
}

describe('CommunityBookingSection', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('방장에게는 연결하기 CTA가 노출된다', () => {
    mockBookings({ data: [] });

    render(<CommunityBookingSection communityId={5} canLink onLinkPress={jest.fn()} />);

    expect(screen.getByLabelText('예약 연결하기')).toBeTruthy();
  });

  it('비방장에게는 연결하기 CTA가 숨겨진다', () => {
    mockBookings({ data: [] });

    render(<CommunityBookingSection communityId={5} canLink={false} onLinkPress={jest.fn()} />);

    expect(screen.queryByLabelText('예약 연결하기')).toBeNull();
  });

  it('연결된 예약이 없으면 빈 상태를 표시한다', () => {
    mockBookings({ data: [] });

    render(<CommunityBookingSection communityId={5} canLink={false} onLinkPress={jest.fn()} />);

    expect(screen.getByText('연결된 예약이 없어요')).toBeTruthy();
  });

  it('연결된 예약은 시설·일시·정원과 함께 카드로 표시된다', () => {
    mockBookings({ data: [BOOKING] });

    render(<CommunityBookingSection communityId={5} canLink={false} onLinkPress={jest.fn()} />);

    expect(screen.getByText('체육관 A · 2026-07-12 14:00~15:00')).toBeTruthy();
    expect(screen.getByText('정원 8명')).toBeTruthy();
  });

  it('비멤버(403)는 잠금 상태 안내를 본다', () => {
    mockBookings({ data: undefined, isError: true, error: forbiddenError() });

    render(<CommunityBookingSection communityId={5} canLink={false} onLinkPress={jest.fn()} />);

    expect(screen.getByText('🔒 멤버만 볼 수 있어요')).toBeTruthy();
  });

  it('403이 아닌 오류는 재시도 가능한 에러 뷰로 표시된다', () => {
    const refetch = jest.fn();
    mockBookings({ data: undefined, isError: true, error: new Error('boom'), refetch });

    render(<CommunityBookingSection communityId={5} canLink={false} onLinkPress={jest.fn()} />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('연결하기 CTA를 탭하면 onLinkPress가 호출된다', () => {
    const onLinkPress = jest.fn();
    mockBookings({ data: [] });

    render(<CommunityBookingSection communityId={5} canLink onLinkPress={onLinkPress} />);
    fireEvent.press(screen.getByLabelText('예약 연결하기'));

    expect(onLinkPress).toHaveBeenCalled();
  });
});

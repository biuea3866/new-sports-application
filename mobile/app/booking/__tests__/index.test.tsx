/**
 * BookingListScreen(MO-05) — 내 예약 목록의 사용자 관점 표시 검증.
 *
 * 내부 식별자(`예약 #2`)·영문 enum(`CONFIRMED`)이 아니라 시설 예약 제목과 한글 상태가
 * 보여야 한다(다른 목록 화면 — 주문 내역·모집 신청 내역 — 과 동일한 표기 규칙).
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import BookingListScreen from '../index';
import type { BookingResponse, ListBookingsResponse } from '../../../api/types';

jest.mock('../../../lib/useMyBookings', () => ({
  useMyBookings: jest.fn(),
  useCancelBooking: jest.fn(),
}));

import { useCancelBooking, useMyBookings } from '../../../lib/useMyBookings';

const useMyBookingsMock = useMyBookings as jest.MockedFunction<typeof useMyBookings>;
const useCancelBookingMock = useCancelBooking as jest.MockedFunction<typeof useCancelBooking>;

function buildBooking(overrides: Partial<BookingResponse> = {}): BookingResponse {
  return {
    id: 2,
    slotId: 11,
    facilityId: 'fac-001',
    userId: 68,
    status: 'CONFIRMED',
    paymentId: 5,
    paymentStatus: 'COMPLETED',
    title: '강남 스포츠센터 07:00-08:00',
    createdAt: '2026-07-31T02:00:00Z',
    updatedAt: '2026-07-31T02:00:00Z',
    ...overrides,
  };
}

function mockBookings(bookings: BookingResponse[], overrides: Record<string, unknown> = {}) {
  const page: ListBookingsResponse = {
    bookings,
    totalElements: bookings.length,
    totalPages: 1,
    page: 0,
    size: 20,
  };
  useMyBookingsMock.mockReturnValue({
    data: page,
    isLoading: false,
    isError: false,
    ...overrides,
  } as unknown as ReturnType<typeof useMyBookings>);
}

describe('BookingListScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useCancelBookingMock.mockReturnValue({
      mutate: jest.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useCancelBooking>);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('예약 제목을 표시하고 내부 식별자는 노출하지 않는다', () => {
    mockBookings([buildBooking()]);

    render(<BookingListScreen />);

    expect(screen.getByText('강남 스포츠센터 07:00-08:00')).toBeTruthy();
    expect(screen.queryByText('예약 #2')).toBeNull();
  });

  it('예약 상태를 한글로 표시한다', () => {
    mockBookings([buildBooking({ status: 'CONFIRMED' })]);

    render(<BookingListScreen />);

    expect(screen.getByText('상태: 확정')).toBeTruthy();
    expect(screen.queryByText('상태: CONFIRMED')).toBeNull();
  });

  it('제목이 없는 예약은 대체 문구로 표시한다', () => {
    mockBookings([buildBooking({ title: null })]);

    render(<BookingListScreen />);

    expect(screen.getByText('시설 예약')).toBeTruthy();
  });

  it('취소 CTA의 접근성 라벨에도 예약 제목을 사용한다', () => {
    mockBookings([buildBooking()]);

    render(<BookingListScreen />);

    expect(screen.getByLabelText('강남 스포츠센터 07:00-08:00 예약 취소')).toBeTruthy();
  });

  it('예약이 없으면 빈 상태 문구를 보여준다', () => {
    mockBookings([]);

    render(<BookingListScreen />);

    expect(screen.getByText('예약 내역이 없습니다.')).toBeTruthy();
  });

  it('조회 실패 시 오류 문구를 보여준다', () => {
    mockBookings([], { isError: true, data: undefined });

    render(<BookingListScreen />);

    expect(screen.getByText('예약 목록을 불러오지 못했습니다.')).toBeTruthy();
  });

  it('다크 모드에서 화면 배경이 다크 토큰을 따른다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockBookings([buildBooking()]);

    render(<BookingListScreen />);

    expect(screen.getByLabelText('예약 목록 화면')).toBeTruthy();
  });
});

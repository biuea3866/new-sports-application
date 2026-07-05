/**
 * BookingNewScreen(MO-04) — 예약 확정 전 대기질 나쁨 이상(BAD/VERY_BAD) 경고·확인 게이트 검증.
 * 근거: FE-16 티켓 "테스트 케이스" · design-fe-app.md FR-13/FR-14
 *
 * useSlots·useCreateBooking·useFacilityDetail·useAirQuality를 모킹해
 * 화면의 경고 배너 노출·확인 게이트·예약 실행 분기만 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { AirQualityResponse, FacilityResponse, SlotResponse } from '../../../api/types';
import BookingNewScreen from '../new';

jest.mock('../../../lib/useBooking', () => ({
  useSlots: jest.fn(),
  useCreateBooking: jest.fn(),
}));
jest.mock('../../../lib/useFacility', () => ({
  useFacilityDetail: jest.fn(),
}));
jest.mock('../../../lib/useAirQuality', () => ({
  useAirQuality: jest.fn(),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSlots, useCreateBooking } from '../../../lib/useBooking';
import { useFacilityDetail } from '../../../lib/useFacility';
import { useAirQuality } from '../../../lib/useAirQuality';

const useSlotsMock = useSlots as jest.MockedFunction<typeof useSlots>;
const useCreateBookingMock = useCreateBooking as jest.MockedFunction<typeof useCreateBooking>;
const useFacilityDetailMock = useFacilityDetail as jest.MockedFunction<typeof useFacilityDetail>;
const useAirQualityMock = useAirQuality as jest.MockedFunction<typeof useAirQuality>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;

const baseSlot: SlotResponse = {
  id: 1,
  date: '2026-07-10',
  timeRange: '10:00-11:00',
  capacity: 10,
} as SlotResponse;

const baseFacility: FacilityResponse = {
  id: 1,
  name: '테스트 체육관',
  gu: '강남구',
  type: 'INDOOR',
  address: '서울시 강남구',
  parking: true,
  tel: '02-1234-5678',
  lat: 37.5,
  lng: 127.0,
  sidoCode: '11',
  sidoName: '서울특별시',
  sigunguCode: '11680',
  sigunguName: '강남구',
};

function mockUseSlotsReturn(overrides: Partial<ReturnType<typeof useSlots>>) {
  useSlotsMock.mockReturnValue({
    data: [baseSlot],
    isLoading: false,
    isError: false,
    ...overrides,
  } as ReturnType<typeof useSlots>);
}

function mockUseCreateBookingReturn(
  mutate: jest.Mock,
  overrides: Partial<ReturnType<typeof useCreateBooking>>
) {
  useCreateBookingMock.mockReturnValue({
    mutate,
    mutateAsync: jest.fn(),
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useCreateBooking>);
}

function mockUseFacilityDetailReturn(data: FacilityResponse | undefined) {
  useFacilityDetailMock.mockReturnValue({
    data,
    isLoading: false,
    isError: false,
  } as unknown as ReturnType<typeof useFacilityDetail>);
}

function mockUseAirQualityReturn(
  overrides: Partial<{
    data: AirQualityResponse | undefined;
    isSuccess: boolean;
    isError: boolean;
  }>
) {
  useAirQualityMock.mockReturnValue({
    data: undefined,
    isSuccess: false,
    isError: false,
    isLoading: false,
    ...overrides,
  } as unknown as ReturnType<typeof useAirQuality>);
}

const badAirQuality: AirQualityResponse = {
  pm10: 90,
  pm25: 45,
  pm10Grade: 'BAD',
  pm25Grade: 'MODERATE',
  representativeGrade: 'BAD',
  stationName: '강남',
  measuredAt: '2026-07-10T09:00:00+09:00',
};

const unknownAirQuality: AirQualityResponse = {
  pm10: null,
  pm25: null,
  pm10Grade: 'UNKNOWN',
  pm25Grade: 'UNKNOWN',
  representativeGrade: 'UNKNOWN',
  stationName: null,
  measuredAt: null,
};

describe('BookingNewScreen', () => {
  let createBookingMock: jest.Mock;

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ facilityId: '1' });
    useRouterMock.mockReturnValue({
      push: jest.fn(),
      replace: jest.fn(),
      back: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    mockUseSlotsReturn({});
    createBookingMock = jest.fn();
    mockUseCreateBookingReturn(createBookingMock, {});
    mockUseFacilityDetailReturn(baseFacility);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('대기질 BAD면 경고 배너가 표시되고 첫 탭에서 예약이 즉시 실행되지 않는다', () => {
    mockUseAirQualityReturn({ data: badAirQuality, isSuccess: true });

    render(<BookingNewScreen />);
    fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));

    expect(screen.getByRole('alert')).toBeTruthy();

    fireEvent.press(screen.getByLabelText('확인하고 예약'));

    expect(createBookingMock).not.toHaveBeenCalled();
  });

  it('확인 후 탭하면 createBooking이 호출된다 — 대기질이 나빠도 예약은 차단되지 않는다', () => {
    mockUseAirQualityReturn({ data: badAirQuality, isSuccess: true });

    render(<BookingNewScreen />);
    fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));
    fireEvent.press(screen.getByLabelText('확인하고 예약'));
    fireEvent.press(screen.getByLabelText('예약 진행'));

    expect(createBookingMock).toHaveBeenCalledWith(
      expect.objectContaining({ slotId: 1 }),
      expect.anything()
    );
  });

  it('대기질 UNKNOWN이면 경고 없이 예약이 정상 진행된다', () => {
    mockUseAirQualityReturn({ data: unknownAirQuality, isSuccess: true });

    render(<BookingNewScreen />);
    fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));

    expect(screen.queryByRole('alert')).toBeNull();

    fireEvent.press(screen.getByLabelText('예약 진행'));

    expect(createBookingMock).toHaveBeenCalledWith(
      expect.objectContaining({ slotId: 1 }),
      expect.anything()
    );
  });

  it('대기질 조회 실패 시 경고 없이 예약이 정상 진행된다', () => {
    mockUseAirQualityReturn({ isError: true });

    render(<BookingNewScreen />);
    fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));

    expect(screen.queryByRole('alert')).toBeNull();

    fireEvent.press(screen.getByLabelText('예약 진행'));

    expect(createBookingMock).toHaveBeenCalledWith(
      expect.objectContaining({ slotId: 1 }),
      expect.anything()
    );
  });

  it('좌표를 확보하지 못하면 경고 없이 예약이 진행된다', () => {
    mockUseFacilityDetailReturn(undefined);
    mockUseAirQualityReturn({});

    render(<BookingNewScreen />);
    fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));

    expect(screen.queryByRole('alert')).toBeNull();

    fireEvent.press(screen.getByLabelText('예약 진행'));

    expect(createBookingMock).toHaveBeenCalledWith(
      expect.objectContaining({ slotId: 1 }),
      expect.anything()
    );
  });
});

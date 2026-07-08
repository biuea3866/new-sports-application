/**
 * BookingNewScreen(MO-04) — 예약 확정 전 대기질 나쁨 이상(BAD/VERY_BAD) 경고·확인 게이트 검증.
 * 근거: FE-16 티켓 "테스트 케이스" · design-fe-app.md FR-13/FR-14
 *
 * FE-28(A-F2): programId 지정 시 program.price 결제 금액 배선 검증.
 * 근거: design-fe-app.md "결제 흐름 재사용 결정"·"API 연동 표" A-F2
 *
 * useSlots·useCreateBooking·useFacilityDetail·useAirQuality·usePrograms를 모킹해
 * 화면의 경고 배너 노출·확인 게이트·예약 실행 분기·program 금액 배선만 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { AirQualityResponse, FacilityResponse, SlotResponse } from '../../../api/types';
import type { ProgramResponse } from '../../../api/program';
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
jest.mock('../../../lib/useProgram', () => ({
  usePrograms: jest.fn(),
}));

import { useLocalSearchParams, useRouter } from 'expo-router';
import { useSlots, useCreateBooking } from '../../../lib/useBooking';
import { useFacilityDetail } from '../../../lib/useFacility';
import { useAirQuality } from '../../../lib/useAirQuality';
import { usePrograms } from '../../../lib/useProgram';

const useSlotsMock = useSlots as jest.MockedFunction<typeof useSlots>;
const useCreateBookingMock = useCreateBooking as jest.MockedFunction<typeof useCreateBooking>;
const useFacilityDetailMock = useFacilityDetail as jest.MockedFunction<typeof useFacilityDetail>;
const useAirQualityMock = useAirQuality as jest.MockedFunction<typeof useAirQuality>;
const usePrograms_Mock = usePrograms as jest.MockedFunction<typeof usePrograms>;
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

const baseProgram: ProgramResponse = {
  id: 9,
  facilityId: '1',
  ownerUserId: 42,
  name: 'PT 1:1',
  description: null,
  price: 50000,
  capacity: 1,
  durationMinutes: 60,
};

function mockUseProgramsReturn(overrides: Partial<ReturnType<typeof usePrograms>>) {
  usePrograms_Mock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...overrides,
  } as unknown as ReturnType<typeof usePrograms>);
}

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
    mockUseProgramsReturn({ data: [] });
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

  describe('program 예약 — programId 지정 시 program.price 배선', () => {
    beforeEach(() => {
      mockUseAirQualityReturn({});
    });

    it('programId가 있으면 program.price를 결제 금액으로 사용해 예약·결제 이동한다', () => {
      useLocalSearchParamsMock.mockReturnValue({ facilityId: '1', programId: '9' });
      mockUseProgramsReturn({ data: [baseProgram], isLoading: false });
      const pushMock = jest.fn();
      useRouterMock.mockReturnValue({
        push: pushMock,
        replace: jest.fn(),
        back: jest.fn(),
      } as unknown as ReturnType<typeof useRouter>);

      render(<BookingNewScreen />);
      fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));
      fireEvent.press(screen.getByLabelText('예약 진행'));

      expect(createBookingMock).toHaveBeenCalledWith(
        expect.objectContaining({ slotId: 1, amount: 50000 }),
        expect.anything()
      );
    });

    it('program 가격을 아직 확보하지 못했으면(목록 로딩 중) 예약 버튼이 비활성화된다', () => {
      useLocalSearchParamsMock.mockReturnValue({ facilityId: '1', programId: '9' });
      mockUseProgramsReturn({ data: undefined, isLoading: true });

      render(<BookingNewScreen />);
      fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));

      const bookButton = screen.getByLabelText('가격 확인 중');
      expect(bookButton.props.accessibilityState.disabled).toBe(true);

      fireEvent.press(bookButton);
      expect(createBookingMock).not.toHaveBeenCalled();
    });

    it('programId가 목록과 매칭되지 않으면 예약이 실행되지 않는다', () => {
      useLocalSearchParamsMock.mockReturnValue({ facilityId: '1', programId: '999' });
      mockUseProgramsReturn({ data: [baseProgram], isLoading: false });

      render(<BookingNewScreen />);
      fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));
      fireEvent.press(screen.getByLabelText('예약 진행'));

      expect(createBookingMock).not.toHaveBeenCalled();
    });

    it('programId 없이 진입하면 기존 고정 금액(10,000원)으로 예약이 진행된다(하위호환)', () => {
      useLocalSearchParamsMock.mockReturnValue({ facilityId: '1' });

      render(<BookingNewScreen />);
      fireEvent.press(screen.getByLabelText('슬롯 10:00-11:00 2026년 7월 10일'));
      fireEvent.press(screen.getByLabelText('예약 진행'));

      expect(createBookingMock).toHaveBeenCalledWith(
        expect.objectContaining({ slotId: 1, amount: 10000 }),
        expect.anything()
      );
    });
  });
});

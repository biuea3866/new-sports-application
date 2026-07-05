/**
 * FacilityDetailScreen — 시/도 표시 + 대기질 카드 통합 검증
 * 근거: design-fe-app.md "Testing Plan" 시설 상세 시나리오, tickets/FE-15 테스트 케이스
 *
 * useFacilityDetail(FE-11)·useAirQuality(FE-12)를 모킹해 화면 배선만 검증한다.
 * AirQualityCard(FE-13)는 재사용 컴포넌트이므로 자체 렌더 결과(문구)로 통합 여부만 확인한다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';

import type { AirQualityResponse, FacilityResponse } from '../../../../api/types';
import FacilityDetailScreen from '../index';

jest.mock('../../../../lib/useFacility', () => ({
  useFacilityDetail: jest.fn(),
}));

jest.mock('../../../../lib/useAirQuality', () => ({
  useAirQuality: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
  router: {
    back: jest.fn(),
    push: jest.fn(),
  },
}));

import { useLocalSearchParams } from 'expo-router';
import { useFacilityDetail } from '../../../../lib/useFacility';
import { useAirQuality } from '../../../../lib/useAirQuality';

const useFacilityDetailMock = useFacilityDetail as jest.MockedFunction<typeof useFacilityDetail>;
const useAirQualityMock = useAirQuality as jest.MockedFunction<typeof useAirQuality>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

const BASE_FACILITY: FacilityResponse = {
  id: 1,
  name: '한강 축구장',
  gu: '광진구',
  type: 'OUTDOOR',
  address: '서울 광진구 자양동 123',
  parking: true,
  tel: '02-1234-5678',
  lat: 37.5,
  lng: 127.1,
  sidoCode: '11',
  sidoName: '서울특별시',
  sigunguCode: '11215',
  sigunguName: '광진구',
};

const SUCCESS_AIR_QUALITY: AirQualityResponse = {
  pm10: 92,
  pm25: 41,
  pm10Grade: 'BAD',
  pm25Grade: 'MODERATE',
  representativeGrade: 'BAD',
  stationName: '광진구 측정소',
  measuredAt: '2026-07-05T14:00:00+09:00',
};

function mockFacilityDetail(overrides: Partial<ReturnType<typeof useFacilityDetail>>): void {
  useFacilityDetailMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    ...overrides,
  } as unknown as ReturnType<typeof useFacilityDetail>);
}

function mockAirQuality(overrides: Partial<ReturnType<typeof useAirQuality>>): void {
  useAirQualityMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...overrides,
  } as unknown as ReturnType<typeof useAirQuality>);
}

describe('FacilityDetailScreen', () => {
  beforeEach(() => {
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    mockAirQuality({ data: undefined, isLoading: false, isError: false });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('시/도명이 있으면 시/도 정보가 표시된다', () => {
    mockFacilityDetail({ data: BASE_FACILITY });

    render(<FacilityDetailScreen />);

    expect(screen.getByText('시/도')).toBeTruthy();
    expect(screen.getByText('서울특별시')).toBeTruthy();
  });

  it('시/도명이 없으면 "지역 미확인"으로 표시된다', () => {
    mockFacilityDetail({ data: { ...BASE_FACILITY, sidoName: '' } });

    render(<FacilityDetailScreen />);

    expect(screen.getByText('지역 미확인')).toBeTruthy();
  });

  it('시/도명이 BE 센티넬 "미지정"이면 "지역 미확인"으로 표시된다', () => {
    mockFacilityDetail({ data: { ...BASE_FACILITY, sidoName: '미지정' } });

    render(<FacilityDetailScreen />);

    expect(screen.getByText('지역 미확인')).toBeTruthy();
    expect(screen.queryByText('미지정')).toBeNull();
  });

  it('시설 좌표로 대기질 카드가 success로 렌더된다', () => {
    mockFacilityDetail({ data: BASE_FACILITY });
    mockAirQuality({ data: SUCCESS_AIR_QUALITY, isLoading: false, isError: false });

    render(<FacilityDetailScreen />);

    expect(screen.getByText('현재 대기질')).toBeTruthy();
    expect(screen.getByText(/PM10/)).toBeTruthy();
    expect(screen.getByText('나쁨')).toBeTruthy();
  });

  it('대기질 조회 실패 시 본체는 정상, 카드만 폴백 문구를 표시한다', () => {
    mockFacilityDetail({ data: BASE_FACILITY });
    mockAirQuality({ data: undefined, isLoading: false, isError: true });

    render(<FacilityDetailScreen />);

    expect(screen.getByText(BASE_FACILITY.name)).toBeTruthy();
    expect(screen.getByText(BASE_FACILITY.address)).toBeTruthy();
    expect(screen.getByText('대기질 정보를 불러올 수 없습니다')).toBeTruthy();
  });

  it('시설이 존재하지 않으면 "시설을 찾을 수 없습니다" 문구가 표시된다', () => {
    mockFacilityDetail({ data: undefined, isLoading: false, isError: false });

    render(<FacilityDetailScreen />);

    expect(screen.getByText('시설을 찾을 수 없습니다')).toBeTruthy();
  });

  it('좌표가 없으면 대기질 카드를 렌더하지 않는다', () => {
    mockFacilityDetail({
      data: { ...BASE_FACILITY, lat: null, lng: null } as unknown as FacilityResponse,
    });
    mockAirQuality({ data: SUCCESS_AIR_QUALITY, isLoading: false, isError: false });

    render(<FacilityDetailScreen />);

    expect(screen.getByText(BASE_FACILITY.name)).toBeTruthy();
    expect(screen.queryByText('현재 대기질')).toBeNull();
    expect(screen.queryByText('대기질 정보를 불러올 수 없습니다')).toBeNull();
  });
});

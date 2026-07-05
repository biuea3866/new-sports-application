/**
 * AirQualityCard — 대기질 조회 상태(loading/error/success)와 pm 부분 결측을 표시하는
 * 프레젠테이션 컴포넌트. success에서도 UNKNOWN·pm 모두 null이면 폴백 문구를 보여준다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AirQualityCard } from '../AirQualityCard';
import type { AirQualityResponse } from '../../api/types';

const SUCCESS_DATA: AirQualityResponse = {
  pm10: 92,
  pm25: 41,
  pm10Grade: 'BAD',
  pm25Grade: 'MODERATE',
  representativeGrade: 'BAD',
  stationName: '광진구 측정소',
  measuredAt: '2026-07-05T14:00:00+09:00',
};

describe('AirQualityCard', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('success에서 PM10/PM2.5 수치와 대표 등급 배지, 측정소·시각이 표시된다', () => {
    render(<AirQualityCard status="success" data={SUCCESS_DATA} />);

    expect(screen.getByText(/PM10/)).toBeTruthy();
    expect(screen.getByText(/92/)).toBeTruthy();
    expect(screen.getByText(/PM2\.5/)).toBeTruthy();
    expect(screen.getByText(/41/)).toBeTruthy();
    expect(screen.getByText('나쁨')).toBeTruthy();
    expect(screen.getByText(/광진구 측정소/)).toBeTruthy();
    expect(screen.getByText(/14:00/)).toBeTruthy();
  });

  it('loading에서 "대기질 정보를 불러오는 중" 문구가 표시된다', () => {
    render(<AirQualityCard status="loading" data={null} />);

    expect(screen.getByText(/대기질 정보를 불러오는 중/)).toBeTruthy();
  });

  it('error에서 "대기질 정보를 불러올 수 없습니다" 폴백이 표시되고 배지는 숨겨진다', () => {
    render(<AirQualityCard status="error" data={null} />);

    expect(screen.getByText('대기질 정보를 불러올 수 없습니다')).toBeTruthy();
    expect(screen.queryByText('나쁨')).toBeNull();
  });

  it('success인데 UNKNOWN·pm 모두 null이면 폴백이 표시되고 배지는 숨겨진다', () => {
    const unknownData: AirQualityResponse = {
      pm10: null,
      pm25: null,
      pm10Grade: 'UNKNOWN',
      pm25Grade: 'UNKNOWN',
      representativeGrade: 'UNKNOWN',
      stationName: null,
      measuredAt: null,
    };

    render(<AirQualityCard status="success" data={unknownData} />);

    expect(screen.getByText('대기질 정보를 불러올 수 없습니다')).toBeTruthy();
    expect(screen.queryByText('정보없음')).toBeNull();
  });

  it('pm10만 있을 때 pm10만 표시되고 크래시하지 않는다', () => {
    const pm10OnlyData: AirQualityResponse = {
      pm10: 55,
      pm25: null,
      pm10Grade: 'MODERATE',
      pm25Grade: 'UNKNOWN',
      representativeGrade: 'MODERATE',
      stationName: '강남구 측정소',
      measuredAt: '2026-07-05T09:30:00+09:00',
    };

    render(<AirQualityCard status="success" data={pm10OnlyData} />);

    expect(screen.getByText(/PM10/)).toBeTruthy();
    expect(screen.getByText(/55/)).toBeTruthy();
    expect(screen.queryByText(/PM2\.5/)).toBeNull();
  });

  it('다크 모드에서도 success 카드가 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<AirQualityCard status="success" data={SUCCESS_DATA} />);

    expect(screen.getByText('나쁨')).toBeTruthy();
  });

  it('measuredAt 형식이 불완전해도 크래시하지 않고 측정소명만 표시한다', () => {
    const malformedTimeData: AirQualityResponse = {
      ...SUCCESS_DATA,
      measuredAt: '2026-07-05',
    };

    render(<AirQualityCard status="success" data={malformedTimeData} />);

    expect(screen.getByText(/광진구 측정소/)).toBeTruthy();
    expect(screen.queryByText(/14:00/)).toBeNull();
  });
});

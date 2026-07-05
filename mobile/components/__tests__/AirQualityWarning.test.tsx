/**
 * AirQualityWarning — 대기질 등급이 나쁨 이상(BAD/VERY_BAD)일 때만 표시되는
 * 예약 전 경고 배너. isBadOrWorse(FE-11) 판정과 useTheme(FE-10) 토큰을 소비한다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AirQualityWarning } from '../AirQualityWarning';
import { lightTokens, darkTokens } from '../../theme/tokens';

describe('AirQualityWarning', () => {
  it('grade가 BAD면 경고 문구와 PM10 값이 표시된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityWarning grade="BAD" pm10={83} />);

    expect(screen.getByText(/나쁨/)).toBeTruthy();
    expect(screen.getByText(/PM10 83/)).toBeTruthy();
  });

  it('grade가 VERY_BAD면 경고가 표시된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityWarning grade="VERY_BAD" pm10={160} />);

    expect(screen.getByText(/매우나쁨/)).toBeTruthy();
  });

  it.each(['GOOD', 'MODERATE', 'UNKNOWN'] as const)(
    'grade가 %s이면 아무것도 렌더하지 않는다',
    (grade) => {
      mockUseColorScheme.mockReturnValue('light');

      render(<AirQualityWarning grade={grade} pm10={20} />);

      expect(screen.queryByRole('alert')).toBeNull();
    }
  );

  it('경고 배너에 alert 접근성 역할이 부여된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityWarning grade="BAD" pm10={83} />);

    expect(screen.getByRole('alert')).toBeTruthy();
  });

  it('pm10이 null이면 수치 없이 경고 문구만 표시된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityWarning grade="BAD" pm10={null} />);

    expect(screen.getByText(/나쁨/)).toBeTruthy();
    expect(screen.queryByText(/PM10/)).toBeNull();
  });

  it('라이트 스킴에서 danger 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityWarning grade="BAD" pm10={83} />);

    expect(screen.getByText(/나쁨/)).toHaveStyle({ color: lightTokens.danger });
  });

  it('다크 스킴에서 danger 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<AirQualityWarning grade="BAD" pm10={83} />);

    expect(screen.getByText(/나쁨/)).toHaveStyle({ color: darkTokens.danger });
  });
});

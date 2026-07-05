/**
 * AirQualityBadge — 등급별 라벨(FE-11 매핑)을 useTheme(FE-10) 토큰 색으로 렌더한다.
 * UNKNOWN은 표시하지 않는다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AirQualityBadge } from '../AirQualityBadge';
import { lightTokens, darkTokens } from '../../theme/tokens';

describe('AirQualityBadge', () => {
  it.each([
    ['GOOD', '좋음'],
    ['MODERATE', '보통'],
    ['BAD', '나쁨'],
    ['VERY_BAD', '매우나쁨'],
  ] as const)('grade=%s이면 "%s" 라벨을 표시한다', (grade, label) => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityBadge grade={grade} />);

    expect(screen.getByText(label)).toBeTruthy();
  });

  it('grade=UNKNOWN이면 아무것도 렌더하지 않는다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityBadge grade="UNKNOWN" />);

    expect(screen.queryByText('정보없음')).toBeNull();
  });

  it('라이트 스킴에서 BAD 등급은 airBadFg 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityBadge grade="BAD" />);

    expect(screen.getByText('나쁨')).toHaveStyle({ color: lightTokens.airBadFg });
  });

  it('다크 스킴에서 BAD 등급은 airBadFg 토큰 색으로 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<AirQualityBadge grade="BAD" />);

    expect(screen.getByText('나쁨')).toHaveStyle({ color: darkTokens.airBadFg });
  });

  it('배지에 접근성 라벨과 role이 부여된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<AirQualityBadge grade="GOOD" />);

    expect(screen.getByLabelText('대기질 등급 좋음')).toBeTruthy();
  });
});

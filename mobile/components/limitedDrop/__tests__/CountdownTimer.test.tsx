/**
 * CountdownTimer — remainingMs를 HH:MM:SS로 표시하고, 0이면 "오픈"으로 표기합니다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { CountdownTimer } from '../CountdownTimer';

describe('CountdownTimer', () => {
  it('remainingMs를 HH:MM:SS로 표시한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<CountdownTimer remainingMs={8093000} />);

    expect(screen.getByText('02:14:53')).toBeTruthy();
  });

  it('remainingMs=0이면 "오픈"으로 표기한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<CountdownTimer remainingMs={0} />);

    expect(screen.getByText('오픈')).toBeTruthy();
  });

  it('remainingMs가 음수여도 "오픈"으로 표기한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<CountdownTimer remainingMs={-1000} />);

    expect(screen.getByText('오픈')).toBeTruthy();
  });

  it('접근성 라이브 리전으로 카운트다운을 노출한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<CountdownTimer remainingMs={5000} />);

    expect(screen.getByLabelText('판매 시작까지 남은 시간')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<CountdownTimer remainingMs={3661000} />);

    expect(screen.getByText('01:01:01')).toBeTruthy();
  });
});

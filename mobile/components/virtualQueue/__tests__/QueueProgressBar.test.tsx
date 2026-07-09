/**
 * QueueProgressBar — ratio 진행바(track+fill) + percentLabel을 렌더합니다.
 * ratio는 [0,1] 범위로 clamp됩니다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { QueueProgressBar } from '../QueueProgressBar';

describe('QueueProgressBar', () => {
  it('ratio 0.5에서 fill width를 50%로 렌더한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueProgressBar ratio={0.5} percentLabel="50%" />);

    expect(screen.getByTestId('queue-progress-fill').props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ width: '50%' })])
    );
  });

  it('percentLabel 텍스트를 렌더한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueProgressBar ratio={0.62} percentLabel="62%" />);

    expect(screen.getByText('62%')).toBeTruthy();
  });

  it('ratio 1.5는 100%로 clamp된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueProgressBar ratio={1.5} percentLabel="100%" />);

    expect(screen.getByTestId('queue-progress-fill').props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ width: '100%' })])
    );
  });

  it('ratio -0.2는 0%로 clamp된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueProgressBar ratio={-0.2} percentLabel="0%" />);

    expect(screen.getByTestId('queue-progress-fill').props.style).toEqual(
      expect.arrayContaining([expect.objectContaining({ width: '0%' })])
    );
  });

  it('접근성 role progressbar를 부착한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueueProgressBar ratio={0.5} percentLabel="50%" />);

    expect(screen.getByRole('progressbar')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<QueueProgressBar ratio={0.3} percentLabel="30%" />);

    expect(screen.getByText('30%')).toBeTruthy();
  });
});

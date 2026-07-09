/**
 * QueuePositionCard — 전달된 순번을 대형 숫자 + "내 순번" 캡션으로 렌더합니다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { QueuePositionCard } from '../QueuePositionCard';

describe('QueuePositionCard', () => {
  it('전달된 순번을 숫자로 렌더한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueuePositionCard position={1240} />);

    expect(screen.getByText('1,240')).toBeTruthy();
  });

  it('"내 순번" 캡션을 함께 렌더한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueuePositionCard position={1} />);

    expect(screen.getByText('내 순번')).toBeTruthy();
  });

  it('접근성 라벨에 순번 정보를 담는다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QueuePositionCard position={7} />);

    expect(screen.getByLabelText('내 순번 7번')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<QueuePositionCard position={42} />);

    expect(screen.getByText('42')).toBeTruthy();
  });
});

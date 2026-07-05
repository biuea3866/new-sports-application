/**
 * LoadingView — 로딩 상태를 스피너 또는 스켈레톤으로 표시한다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { LoadingView } from '../LoadingView';

describe('LoadingView', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('기본(spinner) variant는 로딩 중임을 알리는 progressbar 역할을 노출한다', () => {
    render(<LoadingView />);

    expect(screen.getByRole('progressbar', { name: '로딩 중' })).toBeTruthy();
  });

  it('skeleton variant는 지정한 개수만큼 스켈레톤 카드를 렌더한다', () => {
    render(<LoadingView variant="skeleton" skeletonCount={3} testID="skeleton-item" />);

    expect(screen.getAllByTestId('skeleton-item')).toHaveLength(3);
  });
});

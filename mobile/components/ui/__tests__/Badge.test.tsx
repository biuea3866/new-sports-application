/**
 * Badge — 안읽은 수 원형 배지. count>0이면 숫자를, count<=0이면 아무것도 렌더하지 않는다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { Badge } from '../Badge';

describe('Badge', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('count가 0보다 크면 숫자를 렌더한다', () => {
    render(<Badge count={3} />);

    expect(screen.getByText('3')).toBeTruthy();
  });

  it('count가 0이면 아무것도 렌더하지 않는다', () => {
    render(<Badge count={0} />);

    expect(screen.queryByText('0')).toBeNull();
  });

  it('count가 99를 초과하면 99+로 표시한다', () => {
    render(<Badge count={120} />);

    expect(screen.getByText('99+')).toBeTruthy();
  });
});

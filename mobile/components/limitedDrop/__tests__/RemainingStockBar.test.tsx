/**
 * RemainingStockBar — remaining/limited 수량으로 남은 수량 바를 표시합니다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { RemainingStockBar } from '../RemainingStockBar';

describe('RemainingStockBar', () => {
  it('남은 수량을 "remaining/limited" 형식으로 표시한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<RemainingStockBar remaining={32} limited={100} />);

    expect(screen.getByText('남은 수량 32/100')).toBeTruthy();
  });

  it('remaining=0(소진)이어도 오류 없이 0/limited로 표시한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<RemainingStockBar remaining={0} limited={100} />);

    expect(screen.getByText('남은 수량 0/100')).toBeTruthy();
  });

  it('limited=0이어도 나눗셈 오류 없이 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<RemainingStockBar remaining={0} limited={0} />);

    expect(screen.getByText('남은 수량 0/0')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<RemainingStockBar remaining={10} limited={50} />);

    expect(screen.getByText('남은 수량 10/50')).toBeTruthy();
  });
});

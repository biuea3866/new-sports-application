/**
 * ErrorView — 오류 메시지를 표시하고 재시도 버튼 탭 시 콜백을 호출한다.
 */
import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { ErrorView } from '../ErrorView';

describe('ErrorView', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('메시지를 표시하고 재시도 버튼 탭 시 onRetry 콜백을 호출한다', () => {
    const onRetry = jest.fn();

    render(<ErrorView message="목록을 불러오지 못했어요" onRetry={onRetry} />);

    expect(screen.getByText('목록을 불러오지 못했어요')).toBeTruthy();

    fireEvent.press(screen.getByRole('button', { name: '다시 시도' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });
});

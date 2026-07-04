/**
 * PrimaryButton — 테마 프리미티브 CTA 버튼. disabled 시 disabled 토큰 색 + 접근성 상태를 반영합니다.
 */
import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { PrimaryButton } from '../PrimaryButton';
import { lightTokens } from '../../../theme/tokens';

describe('PrimaryButton', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('활성 상태에서는 accent 토큰 색이며 누르면 onPress가 호출된다', () => {
    const onPress = jest.fn();

    render(<PrimaryButton label="구매하기" onPress={onPress} />);
    const button = screen.getByRole('button', { name: '구매하기' });

    fireEvent.press(button);

    expect(onPress).toHaveBeenCalledTimes(1);
    expect(button).toHaveStyle({ backgroundColor: lightTokens.accent });
  });

  it('disabled=true이면 disabled 토큰 색 + accessibilityState.disabled=true이며 onPress가 호출되지 않는다', () => {
    const onPress = jest.fn();

    render(<PrimaryButton label="구매하기" onPress={onPress} disabled />);
    const button = screen.getByRole('button', { name: '구매하기' });

    fireEvent.press(button);

    expect(onPress).not.toHaveBeenCalled();
    expect(button.props.accessibilityState.disabled).toBe(true);
    expect(button).toHaveStyle({ backgroundColor: lightTokens.disabled });
  });
});

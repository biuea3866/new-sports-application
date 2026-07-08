/**
 * Button — accent/surface variant, disabled·loading 상태를 가진 CTA 프리미티브.
 */
import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { Button } from '../Button';
import { lightTokens } from '../../../theme/tokens';

describe('Button', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('활성 상태에서 누르면 onPress가 1회 호출된다', () => {
    const onPress = jest.fn();

    render(<Button label="채팅방 입장" onPress={onPress} />);
    fireEvent.press(screen.getByRole('button', { name: '채팅방 입장' }));

    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('disabled=true이면 onPress가 호출되지 않고 accessibilityState.disabled가 true다', () => {
    const onPress = jest.fn();

    render(<Button label="채팅방 입장" onPress={onPress} disabled />);
    const button = screen.getByRole('button', { name: '채팅방 입장' });
    fireEvent.press(button);

    expect(onPress).not.toHaveBeenCalled();
    expect(button.props.accessibilityState.disabled).toBe(true);
  });

  it('loading=true이면 로딩 인디케이터를 표시하고 onPress가 호출되지 않는다', () => {
    const onPress = jest.fn();

    render(<Button label="채팅방 입장" onPress={onPress} loading />);
    const button = screen.getByRole('button', { name: '채팅방 입장' });
    fireEvent.press(button);

    expect(onPress).not.toHaveBeenCalled();
    expect(button.props.accessibilityState.busy).toBe(true);
  });

  it('variant="surface"이면 surface 토큰 배경색으로 렌더된다', () => {
    render(<Button label="취소" onPress={jest.fn()} variant="surface" />);

    expect(screen.getByRole('button', { name: '취소' })).toHaveStyle({
      backgroundColor: lightTokens.surface,
    });
  });
});

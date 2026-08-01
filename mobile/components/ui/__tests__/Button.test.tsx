/**
 * Button — accent/surface variant, disabled·loading 상태를 가진 CTA 프리미티브.
 */
import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { Button } from '../Button';
import { darkTokens, lightTokens } from '../../../theme/tokens';

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

  // surface variant는 같은 surface 배경의 Card 안에 놓이는 일이 많다(모집 신청 내역 카드의
  // "취소"). 배경만으로는 버튼 경계가 사라져 맨 텍스트처럼 보이므로 테두리로 구분한다.
  it('variant="surface"이면 border 토큰 테두리로 버튼 경계가 드러난다', () => {
    render(<Button label="취소" onPress={jest.fn()} variant="surface" />);

    expect(screen.getByRole('button', { name: '취소' })).toHaveStyle({
      borderColor: lightTokens.border,
      borderWidth: 1,
    });
  });

  it('variant="accent"이면 테두리를 그리지 않는다', () => {
    render(<Button label="예약하기" onPress={jest.fn()} />);

    expect(screen.getByRole('button', { name: '예약하기' })).toHaveStyle({
      backgroundColor: lightTokens.accent,
      borderWidth: 0,
    });
  });

  it('다크 모드에서도 surface variant 테두리가 다크 토큰을 따른다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<Button label="취소" onPress={jest.fn()} variant="surface" />);

    expect(screen.getByRole('button', { name: '취소' })).toHaveStyle({
      borderColor: darkTokens.border,
    });
  });
});

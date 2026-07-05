/**
 * Card — surface 배경의 카드 컨테이너. onPress 지정 시 탭 가능한 카드가 된다.
 */
import React from 'react';
import { Text } from 'react-native';
import { render, fireEvent, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { Card } from '../Card';
import { lightTokens } from '../../../theme/tokens';

describe('Card', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('children을 surface 배경 위에 렌더한다', () => {
    render(
      <Card testID="card">
        <Text>카드 내용</Text>
      </Card>
    );

    expect(screen.getByText('카드 내용')).toBeTruthy();
    expect(screen.getByTestId('card')).toHaveStyle({ backgroundColor: lightTokens.surface });
  });

  it('onPress 지정 시 버튼 역할이 부여되고 탭하면 콜백이 호출된다', () => {
    const onPress = jest.fn();

    render(
      <Card onPress={onPress} accessibilityLabel="상품 카드">
        <Text>상품</Text>
      </Card>
    );
    fireEvent.press(screen.getByRole('button', { name: '상품 카드' }));

    expect(onPress).toHaveBeenCalledTimes(1);
  });
});

/**
 * ListItem — surface 배경의 리스트 아이템. title/subtitle/leading/trailing을 렌더하고
 * onPress 지정 시 탭 가능하다.
 */
import React from 'react';
import { Text } from 'react-native';
import { render, fireEvent, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { ListItem } from '../ListItem';

describe('ListItem', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('title과 subtitle을 렌더한다', () => {
    render(<ListItem title="주말 축구 모임" subtitle="김철수: 오늘 몇 시에 모여요?" />);

    expect(screen.getByText('주말 축구 모임')).toBeTruthy();
    expect(screen.getByText('김철수: 오늘 몇 시에 모여요?')).toBeTruthy();
  });

  it('trailing 요소를 렌더하고 탭하면 onPress가 호출된다', () => {
    const onPress = jest.fn();

    render(<ListItem title="이영희 (1:1)" trailing={<Text>12:05</Text>} onPress={onPress} />);

    expect(screen.getByText('12:05')).toBeTruthy();
    fireEvent.press(screen.getByRole('button', { name: '이영희 (1:1)' }));
    expect(onPress).toHaveBeenCalledTimes(1);
  });
});

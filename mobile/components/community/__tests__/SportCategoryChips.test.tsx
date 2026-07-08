/**
 * SportCategoryChips — 종목 필터/선택 칩 사용자 관점 동작 검증.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { SportCategoryChips } from '../SportCategoryChips';

describe('SportCategoryChips', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('allLabel을 지정하면 전체 칩이 먼저 렌더된다', () => {
    render(<SportCategoryChips selected={null} onSelect={jest.fn()} allLabel="전체" />);

    expect(screen.getByLabelText('전체')).toBeTruthy();
  });

  it('allLabel을 지정하지 않으면 전체 칩이 렌더되지 않는다', () => {
    render(<SportCategoryChips selected={null} onSelect={jest.fn()} />);

    expect(screen.queryByLabelText('전체')).toBeNull();
  });

  it('종목 칩을 탭하면 onSelect가 해당 종목값으로 호출된다', () => {
    const onSelect = jest.fn();
    render(<SportCategoryChips selected={null} onSelect={onSelect} allLabel="전체" />);

    fireEvent.press(screen.getByLabelText('⚽ 축구'));

    expect(onSelect).toHaveBeenCalledWith('SOCCER');
  });

  it('선택된 칩은 accessibilityState.selected가 true다', () => {
    render(<SportCategoryChips selected="SOCCER" onSelect={jest.fn()} allLabel="전체" />);

    const chip = screen.getByLabelText('⚽ 축구');
    expect(chip.props.accessibilityState.selected).toBe(true);
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    render(<SportCategoryChips selected={null} onSelect={jest.fn()} allLabel="전체" />);

    expect(screen.getByLabelText('전체')).toBeTruthy();
  });
});

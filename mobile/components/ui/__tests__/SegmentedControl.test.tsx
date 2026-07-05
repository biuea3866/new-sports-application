/**
 * SegmentedControl — 공개/비공개 등 옵션 중 하나를 선택하는 세그먼트 컨트롤.
 */
import React from 'react';
import { render, fireEvent, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { SegmentedControl } from '../SegmentedControl';

const OPTIONS = [
  { label: '공개', value: 'PUBLIC' },
  { label: '비공개', value: 'PRIVATE' },
];

describe('SegmentedControl', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('현재 선택된 옵션이 accessibilityState.selected=true로 노출된다', () => {
    render(<SegmentedControl options={OPTIONS} value="PUBLIC" onChange={jest.fn()} />);

    const selected = screen.getByRole('button', { name: '공개' });
    const unselected = screen.getByRole('button', { name: '비공개' });

    expect(selected.props.accessibilityState.selected).toBe(true);
    expect(unselected.props.accessibilityState.selected).toBe(false);
  });

  it('다른 옵션을 탭하면 onChange가 해당 value로 호출된다', () => {
    const onChange = jest.fn();

    render(<SegmentedControl options={OPTIONS} value="PUBLIC" onChange={onChange} />);
    fireEvent.press(screen.getByRole('button', { name: '비공개' }));

    expect(onChange).toHaveBeenCalledWith('PRIVATE');
  });
});

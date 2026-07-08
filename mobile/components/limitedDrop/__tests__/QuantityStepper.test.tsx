/**
 * QuantityStepper — value·max(perUserLimit)로 수량을 증감하고, 경계에서 버튼을 disabled 처리합니다.
 */
import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { QuantityStepper } from '../QuantityStepper';

describe('QuantityStepper', () => {
  it('현재 수량을 표시한다', () => {
    mockUseColorScheme.mockReturnValue('light');

    render(<QuantityStepper value={2} max={5} onChange={jest.fn()} />);

    expect(screen.getByText('2')).toBeTruthy();
  });

  it('증가 버튼을 누르면 value+1로 onChange가 호출된다', () => {
    mockUseColorScheme.mockReturnValue('light');
    const handleChange = jest.fn();

    render(<QuantityStepper value={2} max={5} onChange={handleChange} />);
    fireEvent.press(screen.getByLabelText('수량 증가'));

    expect(handleChange).toHaveBeenCalledWith(3);
  });

  it('감소 버튼을 누르면 value-1로 onChange가 호출된다', () => {
    mockUseColorScheme.mockReturnValue('light');
    const handleChange = jest.fn();

    render(<QuantityStepper value={2} max={5} onChange={handleChange} />);
    fireEvent.press(screen.getByLabelText('수량 감소'));

    expect(handleChange).toHaveBeenCalledWith(1);
  });

  it('value가 max(perUserLimit)와 같으면 증가 버튼을 disabled 처리한다', () => {
    mockUseColorScheme.mockReturnValue('light');
    const handleChange = jest.fn();

    render(<QuantityStepper value={5} max={5} onChange={handleChange} />);
    const increaseButton = screen.getByLabelText('수량 증가');
    fireEvent.press(increaseButton);

    expect(increaseButton.props.accessibilityState.disabled).toBe(true);
    expect(handleChange).not.toHaveBeenCalled();
  });

  it('value가 min(기본값 1)이면 감소 버튼을 disabled 처리한다', () => {
    mockUseColorScheme.mockReturnValue('light');
    const handleChange = jest.fn();

    render(<QuantityStepper value={1} max={5} onChange={handleChange} />);
    const decreaseButton = screen.getByLabelText('수량 감소');
    fireEvent.press(decreaseButton);

    expect(decreaseButton.props.accessibilityState.disabled).toBe(true);
    expect(handleChange).not.toHaveBeenCalled();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<QuantityStepper value={1} max={3} onChange={jest.fn()} />);

    expect(screen.getByText('1')).toBeTruthy();
  });
});

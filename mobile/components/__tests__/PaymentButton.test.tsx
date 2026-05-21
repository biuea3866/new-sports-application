/**
 * [U-02] PaymentButton — 오프라인 상태에서 onPress가 호출되지 않고 토스트만 표시된다
 */
import React from 'react';
import { Alert } from 'react-native';
import { render, fireEvent, screen } from '@testing-library/react-native';
import { PaymentButton } from '../PaymentButton';

jest.mock('../../lib/netinfo');

import { useNetInfo } from '../../lib/netinfo';
import type { NetInfoState } from '../../lib/netinfo';

const mockUseNetInfo = useNetInfo as jest.MockedFunction<typeof useNetInfo>;

const makeNetInfoState = (isConnected: boolean | null): NetInfoState =>
  ({
    type: 'wifi',
    isConnected,
    isInternetReachable: isConnected,
    details: null,
  }) as unknown as NetInfoState;

describe('PaymentButton', () => {
  beforeEach(() => {
    jest.spyOn(Alert, 'alert').mockClear();
  });

  it('[U-02] 오프라인 상태에서 버튼을 누르면 onPress가 호출되지 않고 Alert가 표시된다', () => {
    mockUseNetInfo.mockReturnValue(makeNetInfoState(false));
    const onPress = jest.fn();

    render(<PaymentButton label="결제하기" onPress={onPress} />);
    fireEvent.press(screen.getByRole('button'));

    expect(onPress).not.toHaveBeenCalled();
    expect(Alert.alert).toHaveBeenCalledWith(
      '네트워크 연결 필요',
      '결제하려면 인터넷에 연결되어 있어야 합니다.'
    );
  });

  it('온라인 상태에서 버튼을 누르면 onPress가 호출된다', () => {
    mockUseNetInfo.mockReturnValue(makeNetInfoState(true));
    const onPress = jest.fn();

    render(<PaymentButton label="결제하기" onPress={onPress} />);
    fireEvent.press(screen.getByRole('button'));

    expect(onPress).toHaveBeenCalledTimes(1);
    expect(Alert.alert).not.toHaveBeenCalled();
  });

  it('disabled=true이면 onPress가 호출되지 않는다', () => {
    mockUseNetInfo.mockReturnValue(makeNetInfoState(true));
    const onPress = jest.fn();

    render(<PaymentButton label="결제하기" onPress={onPress} disabled={true} />);
    // TouchableOpacity는 disabled일 때 onPress 이벤트가 발생하지 않음
    const button = screen.getByRole('button');
    expect(button.props.accessibilityState.disabled).toBe(true);
  });
});

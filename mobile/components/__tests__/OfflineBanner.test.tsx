/**
 * [U-01] OfflineBanner — NetInfo.isConnected=false 시 배너가 표시된다
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import { OfflineBanner } from '../OfflineBanner';

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

describe('OfflineBanner', () => {
  it('[U-01] isConnected=false일 때 오프라인 배너가 표시된다', () => {
    mockUseNetInfo.mockReturnValue(makeNetInfoState(false));
    render(<OfflineBanner />);
    // accessibilityLabel로 배너 존재 확인
    expect(screen.getByLabelText('오프라인 상태입니다')).toBeTruthy();
    expect(screen.getByText('네트워크 연결이 없습니다')).toBeTruthy();
  });

  it('isConnected=true이면 배너가 표시되지 않는다', () => {
    mockUseNetInfo.mockReturnValue(makeNetInfoState(true));
    render(<OfflineBanner />);
    expect(screen.queryByLabelText('오프라인 상태입니다')).toBeNull();
  });

  it('isConnected=null(초기 상태)이면 배너가 표시되지 않는다', () => {
    mockUseNetInfo.mockReturnValue(makeNetInfoState(null));
    render(<OfflineBanner />);
    expect(screen.queryByLabelText('오프라인 상태입니다')).toBeNull();
  });
});

/**
 * ConnectionBanner — 연결 끊김/재연결/폴링 폴백 상태 배너.
 * 근거: FE-10 티켓 테스트 케이스 "연결 끊김 시 재연결 배너가 표시되고 폴링 폴백으로 메시지가 계속 갱신된다".
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { ConnectionBanner } from '../ConnectionBanner';

describe('ConnectionBanner', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
  });

  it('연결되어 있으면 배너를 렌더하지 않는다', () => {
    render(<ConnectionBanner isConnected pollingFallback={false} />);

    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('연결이 끊겼고 폴링 폴백 전이면 재연결 중 배너가 표시된다', () => {
    render(<ConnectionBanner isConnected={false} pollingFallback={false} />);

    expect(screen.getByText('연결이 끊겼어요. 재연결 중…')).toBeTruthy();
  });

  it('폴링 폴백 상태이면 폴백 안내 배너가 표시된다', () => {
    render(<ConnectionBanner isConnected={false} pollingFallback />);

    expect(screen.getByText('실시간 연결이 어려워 새로고침으로 갱신하고 있어요')).toBeTruthy();
    expect(screen.queryByText('연결이 끊겼어요. 재연결 중…')).toBeNull();
  });
});

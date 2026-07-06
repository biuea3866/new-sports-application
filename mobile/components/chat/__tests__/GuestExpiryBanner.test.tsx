/**
 * GuestExpiryBanner — 본인 게스트 참여 만료 D-day 안내.
 * 근거: design-fe-app.md S2 와이어프레임 "게스트 참여 만료: D-2".
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import { computeDaysRemaining, GuestExpiryBanner } from '../GuestExpiryBanner';

describe('computeDaysRemaining', () => {
  it('만료 시각이 2일 후이면 2를 반환한다', () => {
    const now = new Date('2026-07-06T00:00:00.000Z');
    const expiresAt = '2026-07-08T00:00:00.000Z';

    expect(computeDaysRemaining(expiresAt, now)).toBe(2);
  });

  it('만료 시각이 이미 지났으면 0 이하를 반환한다', () => {
    const now = new Date('2026-07-06T00:00:00.000Z');
    const expiresAt = '2026-07-05T00:00:00.000Z';

    expect(computeDaysRemaining(expiresAt, now)).toBeLessThanOrEqual(0);
  });
});

describe('GuestExpiryBanner', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-07-06T00:00:00.000Z'));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('expiresAt이 null이면 아무것도 렌더하지 않는다', () => {
    render(<GuestExpiryBanner expiresAt={null} />);

    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('만료가 2일 남았으면 D-2 안내가 렌더된다', () => {
    render(<GuestExpiryBanner expiresAt="2026-07-08T00:00:00.000Z" />);

    expect(screen.getByText('게스트 참여 만료: D-2')).toBeTruthy();
  });

  it('만료일이 오늘이거나 지났으면 D-day 안내가 렌더된다', () => {
    render(<GuestExpiryBanner expiresAt="2026-07-05T00:00:00.000Z" />);

    expect(screen.getByText('게스트 참여 만료: D-day')).toBeTruthy();
  });
});

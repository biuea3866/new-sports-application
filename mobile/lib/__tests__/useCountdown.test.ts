/**
 * U-04: openAt까지 남은 시간을 초 단위로 감소시킨다
 * U-05: openAt이 이미 지났으면 remainingMs=0·isOpen=true를 즉시 반환한다
 * U-06: 언마운트 시 interval이 정리된다 (누수 없음)
 */
import { renderHook } from '@testing-library/react-native';
import { act } from 'react';

import { useCountdown } from '../useCountdown';

describe('useCountdown', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-07-03T09:59:00.000Z'));
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('[U-04] 1초가 지날 때마다 remainingMs가 초 단위로 감소한다', () => {
    const openAtIso = '2026-07-03T10:00:00.000Z'; // 60초 후

    const { result } = renderHook(() => useCountdown(openAtIso));

    expect(result.current.remainingMs).toBe(60_000);
    expect(result.current.isOpen).toBe(false);

    act(() => {
      jest.advanceTimersByTime(1000);
    });

    expect(result.current.remainingMs).toBe(59_000);
    expect(result.current.isOpen).toBe(false);
  });

  it('[U-05] openAt이 이미 지났으면 remainingMs=0·isOpen=true를 즉시 반환한다', () => {
    const pastOpenAtIso = '2026-07-03T09:00:00.000Z'; // 이미 지남

    const { result } = renderHook(() => useCountdown(pastOpenAtIso));

    expect(result.current.remainingMs).toBe(0);
    expect(result.current.isOpen).toBe(true);
  });

  it('[U-05] 카운트다운이 0에 도달하면 isOpen이 true로 전이된다', () => {
    const openAtIso = '2026-07-03T09:59:02.000Z'; // 2초 후

    const { result } = renderHook(() => useCountdown(openAtIso));

    expect(result.current.isOpen).toBe(false);

    act(() => {
      jest.advanceTimersByTime(2000);
    });

    expect(result.current.remainingMs).toBe(0);
    expect(result.current.isOpen).toBe(true);
  });

  it('[U-06] 언마운트 시 interval을 정리한다', () => {
    const clearIntervalSpy = jest.spyOn(global, 'clearInterval');
    const openAtIso = '2026-07-03T10:00:00.000Z';

    const { unmount } = renderHook(() => useCountdown(openAtIso));

    unmount();

    expect(clearIntervalSpy).toHaveBeenCalled();
    clearIntervalSpy.mockRestore();
  });
});

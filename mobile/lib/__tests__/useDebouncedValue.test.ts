/**
 * useDebouncedValue — 값이 delayMs 동안 추가 변경 없이 유지되면 그 값을 반영하는 순수 훅.
 * 근거: FE-08 티켓 "디바운스 로직은 순수 훅/유틸로" (CatalogSearchControls 검색어 디바운스에 사용).
 */
import { renderHook } from '@testing-library/react-native';
import { act } from 'react';

import { useDebouncedValue } from '../useDebouncedValue';

interface HookProps {
  value: string;
}

describe('useDebouncedValue', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('delayMs가 지나기 전에는 초기값을 유지한다', () => {
    const { result } = renderHook(({ value }: HookProps) => useDebouncedValue(value, 300), {
      initialProps: { value: 'a' },
    });

    expect(result.current).toBe('a');
  });

  it('값이 바뀐 후 delayMs가 지나면 새 값으로 갱신된다', () => {
    const { result, rerender } = renderHook(
      ({ value }: HookProps) => useDebouncedValue(value, 300),
      {
        initialProps: { value: 'a' },
      }
    );

    rerender({ value: 'ab' });
    expect(result.current).toBe('a');

    act(() => {
      jest.advanceTimersByTime(300);
    });

    expect(result.current).toBe('ab');
  });

  it('delayMs 이전에 값이 다시 바뀌면 이전 타이머는 취소되고 마지막 값만 반영된다', () => {
    const { result, rerender } = renderHook(
      ({ value }: HookProps) => useDebouncedValue(value, 300),
      {
        initialProps: { value: 'a' },
      }
    );

    rerender({ value: 'ab' });
    act(() => {
      jest.advanceTimersByTime(200);
    });
    rerender({ value: 'abc' });
    act(() => {
      jest.advanceTimersByTime(200);
    });
    expect(result.current).toBe('a');

    act(() => {
      jest.advanceTimersByTime(100);
    });
    expect(result.current).toBe('abc');
  });
});
